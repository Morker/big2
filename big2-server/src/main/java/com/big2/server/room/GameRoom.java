package com.big2.server.room;

import com.big2.core.ai.Bot;
import com.big2.core.ai.SimpleBot;
import com.big2.core.card.Card;
import com.big2.core.game.Game;
import com.big2.core.game.InvalidMoveException;
import com.big2.core.hand.Hand;
import com.big2.server.ClientConnection;
import com.big2.server.protocol.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * A table of four seats. The server is authoritative: every move goes through
 * {@link Game}, and each player is only ever sent their own hand.
 *
 * <p>All public methods are synchronized, so moves from different client threads
 * are applied one at a time.
 */
public final class GameRoom {

    private final String id;
    private final ClientConnection[] seats = new ClientConnection[Game.PLAYER_COUNT];
    private final String[] names = new String[Game.PLAYER_COUNT];
    private final Bot bot = new SimpleBot();
    private Game game;

    public GameRoom(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public synchronized void join(ClientConnection client) {
        if (game != null && !game.isOver()) {
            throw new IllegalStateException("Game already in progress");
        }
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == null) {
                seats[i] = client;
                names[i] = client.name();
                client.send(Protocol.SEAT + " " + i);
                broadcastLobby();
                return;
            }
        }
        throw new IllegalStateException("Room is full");
    }

    /** Starts a new game, filling empty seats with bots. */
    public synchronized void start() {
        if (game != null && !game.isOver()) {
            throw new IllegalStateException("Game already in progress");
        }
        List<String> playerNames = new ArrayList<>();
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == null) {
                names[i] = "Bot" + (i + 1);
            }
            playerNames.add(names[i]);
        }
        game = new Game(playerNames, new Random());
        broadcastLobby();
        for (int i = 0; i < seats.length; i++) {
            sendHand(i);
        }
        afterMove();
    }

    public synchronized void play(ClientConnection client, List<Card> cards) {
        int seat = seatOf(client);
        requireGame();
        try {
            Hand hand = game.play(seat, cards);
            broadcast(Protocol.PLAYED + " " + seat + " " + codes(hand.cards()));
            sendHand(seat);
            afterMove();
        } catch (InvalidMoveException e) {
            client.send(Protocol.ERROR + " " + e.getMessage());
        }
    }

    public synchronized void pass(ClientConnection client) {
        int seat = seatOf(client);
        requireGame();
        try {
            game.pass(seat);
            broadcast(Protocol.PASSED + " " + seat);
            afterMove();
        } catch (InvalidMoveException e) {
            client.send(Protocol.ERROR + " " + e.getMessage());
        }
    }

    /**
     * A player disconnected. Their seat is taken over by a bot so the game can continue.
     * TODO (week 7): hold the seat for ~60 seconds so the player can reconnect.
     */
    public synchronized void leave(ClientConnection client) {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == client) {
                seats[i] = null;
                boolean inProgress = game != null && !game.isOver();
                names[i] = inProgress ? names[i] + "(bot)" : null;
                broadcastLobby();
                if (inProgress && game.currentSeat() == i) {
                    afterMove(); // It was their turn, so a bot plays it.
                }
                return;
            }
        }
    }

    /** Announces the result or the next turn, then lets any bots in a row take their turns. */
    private void afterMove() {
        // TODO (week 7): add a short delay between bot moves and a turn timer for humans.
        while (true) {
            broadcastCounts();
            if (game.isOver()) {
                int winnerSeat = game.players().indexOf(game.winner());
                broadcast(Protocol.WIN + " " + winnerSeat + " " + game.winner().name());
                return;
            }
            int seat = game.currentSeat();
            broadcast(Protocol.TURN + " " + seat + " " + (game.isLeading() ? "LEAD" : "FOLLOW"));
            if (seats[seat] != null) {
                return; // Wait for the human to move.
            }
            Optional<Hand> move = bot.takeTurn(game, seat);
            broadcast(move.map(h -> Protocol.PLAYED + " " + seat + " " + codes(h.cards()))
                    .orElse(Protocol.PASSED + " " + seat));
        }
    }

    private int seatOf(ClientConnection client) {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == client) {
                return i;
            }
        }
        throw new IllegalStateException("You are not seated in this room");
    }

    private void requireGame() {
        if (game == null) {
            throw new IllegalStateException("The game hasn't started. Send START.");
        }
    }

    private void sendHand(int seat) {
        if (seats[seat] != null) {
            seats[seat].send(Protocol.HAND + " " + codes(game.player(seat).hand()));
        }
    }

    private void broadcastLobby() {
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < seats.length; i++) {
            shown.add(names[i] == null ? "-" : names[i]);
        }
        broadcast(Protocol.LOBBY + " " + id + " " + String.join(",", shown));
    }

    private void broadcastCounts() {
        String counts = game.players().stream()
                .map(p -> String.valueOf(p.cardCount()))
                .collect(Collectors.joining(" "));
        broadcast(Protocol.COUNTS + " " + counts);
    }

    private void broadcast(String message) {
        for (ClientConnection c : seats) {
            if (c != null) {
                c.send(message);
            }
        }
    }

    private static String codes(List<Card> cards) {
        return cards.stream().map(Card::code).collect(Collectors.joining(" "));
    }
}
