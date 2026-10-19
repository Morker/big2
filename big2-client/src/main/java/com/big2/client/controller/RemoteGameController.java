package com.big2.client.controller;

import com.big2.core.card.Card;
import com.big2.core.hand.HandEvaluator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Online mode: sends commands to big2-server and turns its messages into {@link TableState}s.
 * See {@code com.big2.server.protocol.Protocol} for the message format.
 *
 * <p>The server is authoritative; this class only mirrors what it is told.
 */
public final class RemoteGameController implements GameController {

    private static final String EMPTY_SEAT = "Empty seat";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    /** Bots answer instantly, so other players' moves are replayed with a short pause to be followable. */
    private static final long MOVE_DELAY_MS = 600;

    private final String host;
    private final int port;
    private final String room;
    private final String playerName;
    private Consumer<TableState> listener = s -> { };
    private PrintWriter out;

    private List<String> names = new ArrayList<>(List.of(EMPTY_SEAT, EMPTY_SEAT, EMPTY_SEAT, EMPTY_SEAT));
    private int[] counts = new int[4];
    private int mySeat = 0;
    private List<Card> hand = List.of();
    private int currentSeat = -1;
    private List<Card> tableCards = List.of();
    private int tableOwner = -1;
    private boolean leading = true;
    private String message = "Connecting...";
    private int winner = -1;

    public RemoteGameController(String host, int port, String room, String playerName) {
        this.host = host;
        this.port = port;
        this.room = room;
        this.playerName = playerName;
    }

    @Override
    public void setListener(Consumer<TableState> listener) {
        this.listener = listener;
    }

    @Override
    public String newGameLabel() {
        return "Start game";
    }

    /** Opens the connection and starts a background reader thread. Set the listener first. */
    public void connect() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        Thread.ofVirtual().name("server-reader").start(() -> {
            try (socket; in) {
                String line;
                while ((line = in.readLine()) != null) {
                    onServerMessage(line);
                }
                onServerMessage("ERROR The server closed the connection.");
            } catch (IOException e) {
                onServerMessage("ERROR Connection lost: " + e.getMessage());
            }
        });
        send("JOIN " + room + " " + playerName);
    }

    @Override
    public void newGame() {
        send("START");
    }

    @Override
    public void play(List<Card> cards) {
        send("PLAY " + cards.stream().map(Card::code).collect(Collectors.joining(" ")));
    }

    @Override
    public void pass() {
        send("PASS");
    }

    private void send(String line) {
        if (out != null) {
            out.println(line);
        }
    }

    /** Called on the reader thread for every line from the server. */
    private void onServerMessage(String line) {
        String[] parts = line.trim().split("\\s+", 2);
        String command = parts[0];
        String rest = parts.length > 1 ? parts[1] : "";

        if ((command.equals("PLAYED") || command.equals("PASSED")) && firstInt(rest) != mySeat) {
            try {
                Thread.sleep(MOVE_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        handle(command, rest);
    }

    private synchronized void handle(String command, String rest) {
        try {
            switch (command) {
                case "WELCOME" -> message = "Connected! Joining the room...";
                case "SEAT" -> mySeat = firstInt(rest);
                case "LOBBY" -> {
                    String[] shown = rest.split("\\s+", 2)[1].split(",");
                    for (int i = 0; i < names.size() && i < shown.length; i++) {
                        names.set(i, shown[i].equals("-") ? EMPTY_SEAT : shown[i]);
                    }
                    if (currentSeat < 0) {
                        message = "Share the server address and room name with your friend, then press Start game. "
                                + "Empty seats are filled by bots.";
                    }
                }
                case "HAND" -> {
                    List<Card> cards = new ArrayList<>(Card.parseAll(rest));
                    Collections.sort(cards);
                    hand = cards;
                    if (cards.size() == 13) { // a fresh deal
                        tableCards = List.of();
                        tableOwner = -1;
                        winner = -1;
                        message = "New game! Whoever has the 3 of diamonds starts.";
                    }
                }
                case "COUNTS" -> {
                    String[] n = rest.split("\\s+");
                    for (int i = 0; i < counts.length && i < n.length; i++) {
                        counts[i] = Integer.parseInt(n[i]);
                    }
                }
                case "TURN" -> {
                    currentSeat = firstInt(rest);
                    leading = rest.endsWith("LEAD");
                    if (leading) {
                        tableCards = List.of();
                        tableOwner = -1;
                    }
                }
                case "PLAYED" -> {
                    String[] p = rest.split("\\s+", 2);
                    int seat = Integer.parseInt(p[0]);
                    tableCards = Card.parseAll(p[1]);
                    tableOwner = seat;
                    String type = HandEvaluator.evaluate(tableCards)
                            .map(h -> h.type().displayName().toLowerCase()).orElse("cards");
                    message = nameOf(seat) + " played " + type + ".";
                }
                case "PASSED" -> message = nameOf(firstInt(rest)) + " passed.";
                case "WIN" -> {
                    winner = firstInt(rest);
                    message = winner == mySeat ? "You win!" : nameOf(winner) + " wins!";
                    message += " Press Start game to play again.";
                }
                case "ERROR" -> message = rest;
                default -> { }
            }
        } catch (RuntimeException e) {
            message = "Unexpected message from server: " + command + " " + rest;
        }
        publish();
    }

    private String nameOf(int seat) {
        return seat == mySeat ? "You" : names.get(seat);
    }

    private static int firstInt(String text) {
        try {
            return Integer.parseInt(text.trim().split("\\s+")[0]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void publish() {
        List<Integer> countList = new ArrayList<>();
        for (int c : counts) {
            countList.add(c);
        }
        listener.accept(new TableState(
                List.copyOf(names),
                countList,
                mySeat,
                List.copyOf(hand),
                currentSeat,
                List.copyOf(tableCards),
                tableOwner,
                leading,
                message,
                winner));
    }
}
