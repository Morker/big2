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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
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
 * and start the match. If they leave, ownership passes to another connected player.
 *
 * <p>Life cycle: {@code lobby} (settings can change) → {@code playing} a deal →
 * {@code between} deals (a short pause showing the scores) → … → back to {@code lobby}
 * when the {@link Match} is over.
 *
 * <p><b>Reconnecting.</b> Each person who sits down gets a secret seat key ({@code TOKEN}).
 * If their connection drops, the seat is held for a grace period: their turns are played by
 * the bot after a short wait, and {@code REJOIN} with the key puts them back with a full
 * snapshot of the table. If nobody at the table is connected, play pauses. Leaving on purpose
 * ({@code QUIT}) gives the seat up at once.
 *
 * <p>All state changes happen while holding this object's lock: the public methods are
 * synchronized, and scheduled tasks (bot moves, turn timers, the next deal, grace periods)
 * lock it too. Every scheduled task carries a token, so a task that was overtaken by events does nothing.
 */
public final class GameRoom {

    private static final long TIMER_GRACE_MS = 400;
    private static final List<String> BOT_NAMES = List.of("Ali", "Mei", "Raj", "Kim", "Leo", "Ana");
    /** Reactions players can send; the client turns each id into an emoji. */
    static final Set<String> REACTIONS = Set.of("like", "lol", "wow", "cool", "cry", "angry", "fire", "clap", "gg");
    private static final long REACTION_GAP_MS = 1200;
    private static final SecureRandom KEYS = new SecureRandom();
    private static final String BOT_SUFFIX = "(bot)";

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

    /** The open connection for each seat, or null (empty, bot, or person away). */
    private final PlayerSession[] seats = new PlayerSession[Game.PLAYER_COUNT];
    /** Seat keys: non-null means a person holds this seat, connected or not. */
    private final String[] keys = new String[Game.PLAYER_COUNT];
    private final String[] names = new String[Game.PLAYER_COUNT];
    private final ScheduledFuture<?>[] graceTimers = new ScheduledFuture<?>[Game.PLAYER_COUNT];
    private final long[] lastReaction = new long[Game.PLAYER_COUNT];
    private final Random random = new Random();
    private Bot bot = BotLevel.NORMAL.create();

    private GameSettings settings = GameSettings.DEFAULT;
    private Phase phase = Phase.LOBBY;
    private int owner = -1;
    private Match match;
    private Game game;
    private ScheduledFuture<?> pending;
    private int token;
    private long turnEndsAt;
    private long nextDealAt;
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
            if (keys[i] == null && seats[i] == null) {
                seats[i] = player;
                keys[i] = newKey();
                names[i] = uniqueName(player.name());
                if (owner < 0) {
                    owner = i;
                }
                player.send(Protocol.SEAT + " " + i);
                player.send(Protocol.TOKEN + " " + keys[i]);
                player.send(Protocol.SETTINGS + " " + settings.toWire());
                player.send(Protocol.PHASE + " " + phase.id());
                sendAwaySeats(player, i);
                broadcastLobby();
                broadcast(Protocol.OWNER + " " + owner);
                return;
            }
        }
        throw new IllegalStateException("Room " + id + " is full");
    }

    /** Puts a returning player back in their seat, with everything they need to carry on. */
    public synchronized void rejoin(PlayerSession player, String key) {
        int seat = -1;
        for (int i = 0; i < keys.length; i++) {
            if (key != null && key.equals(keys[i])) {
                seat = i;
            }
        }
        if (closed || seat < 0) {
            throw new IllegalStateException("Your seat in room " + id + " was given up. Join or create a new game.");
        }
        boolean nobodyWasHere = !anyConnected();
        PlayerSession previous = seats[seat];
        if (previous != null && previous != player) {
            previous.send(Protocol.ERROR + " You opened the game in another window, so this one was disconnected.");
        }
        seats[seat] = player;
        cancelGrace(seat);

        if (owner < 0 || seats[owner] == null) {
            owner = seat; // the owner is away: whoever is here can run the table
        }
        sendSnapshot(seat);
        broadcast(Protocol.BACK + " " + seat);
        broadcast(Protocol.OWNER + " " + owner);

        if (phase == Phase.PLAYING && (nobodyWasHere || game.currentSeat() == seat)) {
            nextTurn(); // resume a paused table, or restart this player's own turn timer
        } else if (phase == Phase.BETWEEN && nobodyWasHere) {
            int t = ++token;
            long wait = Math.max(2000, nextDealAt - System.currentTimeMillis());
            pending = later(wait, () -> nextDeal(t));
        }
    }

    /** The connection dropped: hold the seat for a while so the player can come back. */
    public synchronized void leave(PlayerSession player) {
        int seat = indexOf(player);
        if (seat < 0) {
            return;
        }
        seats[seat] = null;
        String key = keys[seat];
        long grace = phase == Phase.LOBBY ? timing.lobbyGraceMs() : timing.gameGraceMs();
        graceTimers[seat] = later(grace, () -> graceOver(seat, key));
        broadcast(Protocol.AWAY + " " + seat);

        if (owner == seat) {
            int other = firstConnectedSeat();
            if (other >= 0) {
                owner = other;
                broadcast(Protocol.OWNER + " " + owner);
            }
        }
        if (!anyConnected()) {
            cancelPending(); // everyone is away: pause until someone returns
        } else if (phase == Phase.PLAYING && game.currentSeat() == seat) {
            nextTurn(); // switch their running turn over to the away handling
        }
    }

    /** The player left on purpose: give the seat up straight away. */
    public synchronized void quit(PlayerSession player) {
        int seat = indexOf(player);
        if (seat >= 0) {
            release(seat);
        }
    }

    private synchronized void graceOver(int seat, String key) {
        if (key != null && key.equals(keys[seat]) && seats[seat] == null) {
            release(seat);
        }
    }

    /** Frees a seat for good: empty in the lobby, a bot during a game. */
    private void release(int seat) {
        cancelGrace(seat);
        seats[seat] = null;
        keys[seat] = null;
        if (phase == Phase.LOBBY) {
            names[seat] = null;
        } else if (!names[seat].endsWith(BOT_SUFFIX)) {
            names[seat] = names[seat] + BOT_SUFFIX;
        }

        if (Arrays.stream(keys).allMatch(k -> k == null)) {
            closed = true;
            cancelPending();
            for (int i = 0; i < graceTimers.length; i++) {
                cancelGrace(i);
            }
            onEmpty.accept(this);
            return;
        }
        if (owner == seat) {
            owner = firstConnectedSeat() >= 0 ? firstConnectedSeat() : firstHeldSeat();
        }
        broadcast(Protocol.LEFT + " " + seat);
        broadcastLobby();
        broadcast(Protocol.OWNER + " " + owner);
        if (phase == Phase.PLAYING && game.currentSeat() == seat && anyConnected()) {
            nextTurn(); // a bot takes it from here
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
            if (keys[i] == null) {
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

    /** Shows a reaction next to the player's seat for everyone. Limited to one every ~1.2 seconds. */
    public synchronized void react(PlayerSession player, String id) {
        int seat = seatOf(player);
        if (!REACTIONS.contains(id)) {
            throw new IllegalArgumentException("Unknown reaction: " + id);
        }
        long now = System.currentTimeMillis();
        if (now - lastReaction[seat] < REACTION_GAP_MS) {
            return; // quietly drop rapid repeats
        }
        lastReaction[seat] = now;
        broadcast(Protocol.REACT + " " + seat + " " + id);
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

    /**
     * Announces the next turn, then waits for the connected player's move (with their timer),
     * gives an away player a short while before the bot plays for them, or lets the bot play.
     */
    private void nextTurn() {
        cancelPending();
        broadcastCounts();
        if (game.isOver()) {
            endDeal();
            return;
        }
        int seat = game.currentSeat();
        boolean here = seats[seat] != null;
        boolean away = !here && keys[seat] != null;
        int seconds = here ? settings.turnSeconds() : 0;
        turnEndsAt = seconds > 0 ? System.currentTimeMillis() + seconds * 1000L : 0;
        broadcast(Protocol.TURN + " " + seat + " " + (game.isLeading() ? "LEAD" : "FOLLOW") + " " + seconds);

        if (!anyConnected()) {
            return; // paused until someone comes back
        }
        int t = ++token;
        if (here) {
            if (seconds > 0) {
                pending = later(seconds * 1000L + TIMER_GRACE_MS, () -> timedOut(t));
            }
        } else if (away) {
            pending = later(timing.awayTurnMs(), () -> botTurn(t));
        } else {
            pending = later(timing.botDelayMs(), () -> botTurn(t));
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
                if (keys[i] == null) {
                    names[i] = null; // Free bot seats so friends can join the next game.
                }
            }
            broadcast(Protocol.PHASE + " " + phase.id());
            broadcastLobby();
        } else {
            phase = Phase.BETWEEN;
            broadcast(Protocol.PHASE + " " + phase.id());
            broadcast(Protocol.NEXT + " " + timing.nextDealSeconds());
            nextDealAt = System.currentTimeMillis() + timing.nextDealSeconds() * 1000L;
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

    /** Everything a returning player needs to redraw the table, wrapped in RESUME … RESUMED. */
    private void sendSnapshot(int seat) {
        PlayerSession p = seats[seat];
        p.send(Protocol.RESUME);
        p.send(Protocol.SEAT + " " + seat);
        p.send(Protocol.TOKEN + " " + keys[seat]);
        p.send(Protocol.SETTINGS + " " + settings.toWire());
        p.send(lobbyLine());
        p.send(Protocol.OWNER + " " + owner);
        p.send(Protocol.PHASE + " " + phase.id());
        sendAwaySeats(p, seat);
        if (match != null && phase != Phase.LOBBY) {
            p.send(Protocol.DEAL + " " + (phase == Phase.PLAYING ? match.dealsPlayed() + 1 : match.dealsPlayed()));
            if (match.dealsPlayed() > 0) {
                p.send(Protocol.SCORES + " " + join(match.lastPenalties()) + " " + join(match.scores()));
            }
            p.send(Protocol.HAND + " " + codes(game.player(seat).hand()));
            p.send(countsLine());
            if (phase == Phase.PLAYING) {
                if (game.lastPlay() != null) {
                    p.send(Protocol.TABLE + " " + game.lastPlaySeat() + " " + codes(game.lastPlay().cards()));
                }
                long left = turnEndsAt > 0 ? Math.max(0, (turnEndsAt - System.currentTimeMillis() + 999) / 1000) : 0;
                p.send(Protocol.TURN + " " + game.currentSeat() + " " + (game.isLeading() ? "LEAD" : "FOLLOW") + " " + left);
            } else {
                int winner = game.winnerSeat();
                p.send(Protocol.WIN + " " + winner + " " + names[winner]);
                p.send(Protocol.NEXT + " " + Math.max(1, (nextDealAt - System.currentTimeMillis() + 999) / 1000));
            }
        }
        p.send(Protocol.RESUMED);
    }

    private void sendAwaySeats(PlayerSession p, int self) {
        for (int i = 0; i < seats.length; i++) {
            if (i != self && keys[i] != null && seats[i] == null) {
                p.send(Protocol.AWAY + " " + i);
            }
        }
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

    private void cancelGrace(int seat) {
        if (graceTimers[seat] != null) {
            graceTimers[seat].cancel(false);
            graceTimers[seat] = null;
        }
    }

    private static String newKey() {
        byte[] bytes = new byte[16];
        KEYS.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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

    private boolean anyConnected() {
        return firstConnectedSeat() >= 0;
    }

    private int firstConnectedSeat() {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null) {
                return i;
            }
        }
        return -1;
    }

    private int firstHeldSeat() {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null) {
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

    private String lobbyLine() {
        List<String> shown = new ArrayList<>();
        for (String n : names) {
            shown.add(n == null ? "-" : n);
        }
        return Protocol.LOBBY + " " + id + " " + String.join(",", shown);
    }

    private void broadcastLobby() {
        broadcast(lobbyLine());
    }

    private String countsLine() {
        return Protocol.COUNTS + " " + game.players().stream()
                .map(p -> String.valueOf(p.cardCount()))
                .collect(Collectors.joining(" "));
    }

    private void broadcastCounts() {
        broadcast(countsLine());
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
