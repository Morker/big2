package com.big2.server;

import com.big2.core.card.Card;
import com.big2.server.protocol.Protocol;
import com.big2.server.room.GameRoom;
import com.big2.server.room.RoomManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One connected player. Reads commands from the socket and forwards them to its {@link GameRoom}.
 * The room does all rule checking; this class only parses text.
 */
public final class ClientConnection implements Runnable {

    private final Socket socket;
    private final RoomManager rooms;
    private PrintWriter out;
    private GameRoom room;
    private String name = "?";

    public ClientConnection(Socket socket, RoomManager rooms) {
        this.socket = socket;
        this.rooms = rooms;
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            send(Protocol.WELCOME + " Big 2 server. Commands: JOIN <room> <name>, START, PLAY <cards>, PASS, QUIT");

            String line;
            while ((line = in.readLine()) != null) {
                if (!handle(line.trim())) {
                    break;
                }
            }
        } catch (IOException e) {
            // Client disconnected abruptly; fall through to cleanup.
        } finally {
            if (room != null) {
                room.leave(this);
            }
        }
    }

    /** Returns false when the client wants to quit. */
    private boolean handle(String line) {
        if (line.isEmpty()) {
            return true;
        }
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toUpperCase();
        String rest = parts.length > 1 ? parts[1] : "";

        try {
            switch (command) {
                case Protocol.JOIN -> join(rest);
                case Protocol.START -> requireRoom().start();
                case Protocol.PLAY -> {
                    List<Card> cards = Card.parseAll(rest);
                    requireRoom().play(this, cards);
                }
                case Protocol.PASS -> requireRoom().pass(this);
                case Protocol.QUIT -> {
                    return false;
                }
                default -> send(Protocol.ERROR + " Unknown command: " + command);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(Protocol.ERROR + " " + e.getMessage());
        }
        return true;
    }

    private void join(String args) {
        if (room != null) {
            throw new IllegalStateException("Already in room " + room.id());
        }
        String[] parts = args.split("\\s+");
        if (parts.length < 2 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("Usage: JOIN <room> <name>");
        }
        name = parts[1];
        GameRoom target = rooms.getOrCreate(parts[0]);
        target.join(this);
        room = target;
    }

    private GameRoom requireRoom() {
        if (room == null) {
            throw new IllegalStateException("JOIN a room first");
        }
        return room;
    }

    public String name() {
        return name;
    }

    /** Thread-safe: rooms may call this from another player's thread. */
    public synchronized void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }
}
