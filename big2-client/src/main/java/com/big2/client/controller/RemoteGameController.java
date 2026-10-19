package com.big2.client.controller;

import com.big2.core.card.Card;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Online mode: sends commands to big2-server and turns its messages into {@link TableState}s.
 * See {@code com.big2.server.protocol.Protocol} for the message format.
 *
 * <p>STATUS: skeleton. Connecting and sending commands work; turning server
 * messages into a TableState is your week 6 task (see {@link #onServerMessage}).
 */
public final class RemoteGameController implements GameController {

    private final String host;
    private final int port;
    private final String room;
    private final String playerName;
    private Consumer<TableState> listener = s -> { };
    private PrintWriter out;

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

    /** Opens the connection and starts a background reader thread. */
    public void connect() throws IOException {
        Socket socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        Thread.ofVirtual().name("server-reader").start(() -> {
            try (socket; in) {
                String line;
                while ((line = in.readLine()) != null) {
                    onServerMessage(line);
                }
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

    /**
     * Called on the reader thread for every line from the server.
     *
     * TODO (week 6):
     *  1. Keep fields for names, counts, mySeat, myHand, currentSeat, tableCards, ...
     *  2. Update them from SEAT, LOBBY, HAND, COUNTS, TURN, PLAYED, PASSED, WIN, ERROR.
     *  3. Build a TableState and deliver it with SwingUtilities.invokeLater(() -> listener.accept(state)).
     */
    private void onServerMessage(String line) {
        System.out.println("[server] " + line);
    }
}
