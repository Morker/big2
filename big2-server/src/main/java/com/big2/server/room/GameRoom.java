package com.big2.server.room;

import com.big2.core.ai.Bot;
import com.big2.core.ai.BotLevel;
import com.big2.core.card.Card;
import com.big2.core.game.Game;
import com.big2.core.game.InvalidMoveException;
import com.big2.core.hand.Hand;
import com.big2.core.match.GameSettings;
import com.big2.core.match.Match;
import com.big2.server.PlayerSession;
import com.big2.server.protocol.Protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A table of four seats. The server is authoritative: every move goes through
 * {@link Game}, and each player is only ever sent their own hand.
 *
 * <p>The first person to sit down owns the room: only they change the {@link GameSettings}
 * and start the match. If they leave, ownership passes to the next human seat.
 *
 * <p>Life cycle: {@code lobby} (settings can change) → {@code playing} a deal →
 * {@code between} deals (a short pause showing the scores) → … → back to {@code lobby}
 * when the {@link Match} is over.
 *
 * <p>All state changes happen while holding this object's lock: the public methods are
 * synchronized, and scheduled tasks (bot moves, turn timers, the next deal) lock it too.
 * Every scheduled task carries a token, so a task that was overtaken by events does nothing.
 */
public final class GameRoom {

    private static final long TIMER_GRACE_MS = 400;
    private static final List<String> BOT_NAMES = List.of("Ali", "Mei", "Raj", "Kim", "Leo", "Ana");

    private enum Phase {
        LOBBY, PLAYING, BETWEEN;

        String id() {
            return name().toLowerCase();
        }
    }

    private final String id;
    private final ScheduledExecutorService scheduler;
    private final RoomTiming timing;
    private final Consumer<GameRoom> onEmpty;
    private final PlayerSession[] seats = new PlayerSession[Game.PLAYER_COUNT];
    private final String[] names = new String[Game.PLAYER_COUNT];
    private Bot bot = BotLevel.NORMAL.create();
    private final Random random = new Random();

    private GameSettings settings = GameSettings.DEFAULT;
    private Phase phase = Phase.LOBBY;
    private int owner = -1;
    private Match match;
    private Game game;
    private ScheduledFuture<?> pending;
    private int token;
    private boolean closed;

    GameRoom(String id, ScheduledExecutorService scheduler, RoomTiming timing, Consumer<GameRoom> onEmpty) {
        this.id = id;
        this.scheduler = scheduler;
        this.timing = timing;
        this.onEmpty = onEmpty;
    }

    public String id() {
        return id;
    }

    // ---- Seating ----

    public synchronized void join(PlayerSession player) {
        if (closed) {
            throw new IllegalStateException("That room just closed. Create a new one.");
        }
        if (phase != Phase.LOBBY) {
            throw new IllegalStateException("A game is in progress in room " + id + ". Try again when it ends.");
        }
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == null) {
                seats[i] = player;
                names[i] = uniqueName(player.name());
                if (owner < 0) {
                    owner = i;
                }
                player.send(Protocol.SEAT + " " + i);
                player.send(Protocol.SETTINGS + " " + settings.toWire());
                player.send(Protocol.PHASE + " " + phase.id());
                broadcastLobby();
                broadcast(Protocol.OWNER + " " + owner);
                return;
            }
        }
        throw new IllegalStateException("Room " + id + " is full");
    }

    /** A player disconnected. Mid-match a bot takes over their seat so the others can carry on. */
    public synchronized void leave(PlayerSession player) {
        int seat = indexOf(player);
        if (seat < 0) {
            return;
        }
        seats[seat] = null;
        names[seat] = phase == Phase.LOBBY ? null : names[seat] + "(bot)";

        if (Arrays.stream(seats).allMatch(s -> s == null)) {
            closed = true;
            cancelPending();
            onEmpty.accept(this);
            return;
        }
        if (owner == seat) {
            owner = firstHumanSeat();
        }
        broadcastLobby();
        broadcast(Protocol.OWNER + " " + owner);
        if (phase == Phase.PLAYING && game.currentSeat() == seat) {
            nextTurn(); // It was their turn: hand it to the bot now instead of waiting for the timer.
        }
    }

    // ---- Owner controls ----

    public synchronized void set(PlayerSession player, String key, String value) {
        requireOwner(player);
        if (phase != Phase.LOBBY) {
            throw new IllegalStateException("Settings can only be changed between games");
        }
        settings = settings.with(key, value);
        broadcast(Protocol.SETTINGS + " " + settings.toWire());
    }

    public synchronized void start(PlayerSession player) {
        requireOwner(player);
        if (phase != Phase.LOBBY) {
            throw new IllegalStateException("The game has already started");
        }
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == null) {
                names[i] = botName();
            }
        }
        match = new Match(settings);
        bot = settings.bots().create();
        broadcastLobby();
        startDeal();
    }

    // ---- Moves ----

    public synchronized void play(PlayerSession player, List<Card> cards) {
        int seat = seatOf(player);
        requirePlaying();
        try {
            Hand hand = game.play(seat, cards);
            broadcast(Protocol.PLAYED + " " + seat + " " + codes(hand.cards()));
            sendHand(seat);
            nextTurn();
        } catch (InvalidMoveException e) {
            player.send(Protocol.ERROR + " " + e.getMessage());
        }
    }

    public synchronized void pass(PlayerSession player) {
        int seat = seatOf(player);
        requirePlaying();
        try {
            game.pass(seat);
            broadcast(Protocol.PASSED + " " + seat);
            nextTurn();
        } catch (InvalidMoveException e) {
            player.send(Protocol.ERROR + " " + e.getMessage());
        }
    }

    // ---- Match flow (always called with the lock held) ----

    private void startDeal() {
        phase = Phase.PLAYING;
        game = match.startDeal(List.of(names), random);
        broadcast(Protocol.PHASE + " " + phase.id());
        broadcast(Protocol.DEAL + " " + (match.dealsPlayed() + 1));
        for (int i = 0; i < seats.length; i++) {
            sendHand(i);
        }
        nextTurn();
    }

    /** Announces the next turn, and schedules the bot's move or the human's timer. */
    private void nextTurn() {
        cancelPending();
        broadcastCounts();
        if (game.isOver()) {
            endDeal();
            return;
        }
        int seat = game.currentSeat();
        boolean human = seats[seat] != null;
        int seconds = human ? settings.turnSeconds() : 0;
        broadcast(Protocol.TURN + " " + seat + " " + (game.isLeading() ? "LEAD" : "FOLLOW") + " " + seconds);

        int t = ++token;
        if (!human) {
            pending = later(timing.botDelayMs(), () -> botTurn(t));
        } else if (seconds > 0) {
            pending = later(seconds * 1000L + TIMER_GRACE_MS, () -> timedOut(t));
        }
    }

    private synchronized void botTurn(int t) {
        if (t != token || phase != Phase.PLAYING) {
            return;
        }
        int seat = game.currentSeat();
        Optional<Hand> move = bot.takeTurn(game, seat);
        broadcast(move.map(h -> Protocol.PLAYED + " " + seat + " " + codes(h.cards()))
                .orElse(Protocol.PASSED + " " + seat));
        nextTurn();
    }

    /** The timer ran out: pass if allowed, otherwise lead the lowest card (which is 3♦ on the opening play). */
    private synchronized void timedOut(int t) {
        if (t != token || phase != Phase.PLAYING) {
            return;
        }
        int seat = game.currentSeat();
        broadcast(Protocol.TIMEOUT + " " + seat);
        if (game.isLeading()) {
            Card lowest = game.player(seat).hand().get(0);
            game.play(seat, List.of(lowest));
            broadcast(Protocol.PLAYED + " " + seat + " " + lowest.code());
            sendHand(seat);
        } else {
            game.pass(seat);
            broadcast(Protocol.PASSED + " " + seat);
        }
        nextTurn();
    }

    private void endDeal() {
        match.finishDeal();
        int winner = game.winnerSeat();
        broadcast(Protocol.WIN + " " + winner + " " + names[winner]);
        broadcast(Protocol.SCORES + " " + join(match.lastPenalties()) + " " + join(match.scores()));

        if (match.isOver()) {
            int best = match.leaderSeat();
            broadcast(Protocol.MATCH + " " + best + " " + names[best]);
            phase = Phase.LOBBY;
            for (int i = 0; i < seats.length; i++) {
                if (seats[i] == null) {
                    names[i] = null; // Free bot seats so friends can join the next game.
                }
            }
            broadcast(Protocol.PHASE + " " + phase.id());
            broadcastLobby();
        } else {
            phase = Phase.BETWEEN;
            broadcast(Protocol.PHASE + " " + phase.id());
            broadcast(Protocol.NEXT + " " + timing.nextDealSeconds());
            int t = ++token;
            pending = later(timing.nextDealSeconds() * 1000L, () -> nextDeal(t));
        }
    }

    private synchronized void nextDeal(int t) {
        if (t != token || phase != Phase.BETWEEN) {
            return;
        }
        startDeal();
    }

    // ---- Helpers ----

    private ScheduledFuture<?> later(long delayMs, Runnable task) {
        return scheduler.schedule(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                System.err.println("Room " + id + ": scheduled task failed: " + e);
                e.printStackTrace();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void cancelPending() {
        token++;
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
    }

    private void requireOwner(PlayerSession player) {
        if (seatOf(player) != owner) {
            throw new IllegalStateException("Only the room owner can do that");
        }
    }

    private void requirePlaying() {
        if (phase != Phase.PLAYING) {
            throw new IllegalStateException(phase == Phase.LOBBY
                    ? "The game hasn't started yet" : "Wait for the next deal");
        }
    }

    private int indexOf(PlayerSession player) {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == player) {
                return i;
            }
        }
        return -1;
    }

    private int seatOf(PlayerSession player) {
        int seat = indexOf(player);
        if (seat < 0) {
            throw new IllegalStateException("You are not seated in this room");
        }
        return seat;
    }

    private int firstHumanSeat() {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null) {
                return i;
            }
        }
        return -1;
    }

    private String uniqueName(String wanted) {
        String candidate = wanted;
        for (int n = 2; isTaken(candidate); n++) {
            candidate = wanted + n;
        }
        return candidate;
    }

    private boolean isTaken(String name) {
        return Arrays.stream(names).anyMatch(name::equalsIgnoreCase);
    }

    private String botName() {
        List<String> free = new ArrayList<>();
        for (String n : BOT_NAMES) {
            if (!isTaken(n)) {
                free.add(n);
            }
        }
        return free.isEmpty() ? uniqueName("Bot") : free.get(random.nextInt(free.size()));
    }

    private void sendHand(int seat) {
        if (seats[seat] != null) {
            seats[seat].send(Protocol.HAND + " " + codes(game.player(seat).hand()));
        }
    }

    private void broadcastLobby() {
        List<String> shown = new ArrayList<>();
        for (String n : names) {
            shown.add(n == null ? "-" : n);
        }
        broadcast(Protocol.LOBBY + " " + id + " " + String.join(",", shown));
    }

    private void broadcastCounts() {
        broadcast(Protocol.COUNTS + " " + game.players().stream()
                .map(p -> String.valueOf(p.cardCount()))
                .collect(Collectors.joining(" ")));
    }

    private void broadcast(String message) {
        for (PlayerSession s : seats) {
            if (s != null) {
                s.send(message);
            }
        }
    }

    private static String join(int[] values) {
        return Arrays.stream(values).mapToObj(String::valueOf).collect(Collectors.joining(" "));
    }

    private static String codes(List<Card> cards) {
        return cards.stream().map(Card::code).collect(Collectors.joining(" "));
    }
}
