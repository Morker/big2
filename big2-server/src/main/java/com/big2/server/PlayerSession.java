package com.big2.server;

import com.big2.core.card.Card;
import com.big2.server.protocol.Protocol;
import com.big2.server.room.GameRoom;
import com.big2.server.room.RoomManager;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * One connected player, independent of transport. TCP ({@link ClientConnection}) and
 * WebSocket ({@link WebServer}) both feed lines into {@link #handle} and pass in how to send.
 * The room does all rule checking; this class only parses text.
 */
public final class PlayerSession {

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_.-]{1,16}");

    private final RoomManager rooms;
    private final Consumer<String> out;
    private GameRoom room;
    private String name = "?";
    private boolean closed;

    public PlayerSession(RoomManager rooms, Consumer<String> out) {
        this.rooms = rooms;
        this.out = out;
    }

    public void welcome() {
        send(Protocol.WELCOME + " Big 2 server. Commands: CREATE <name>, JOIN <room> <name>, SET <key> <value>, START, PLAY <cards>, PASS, QUIT");
    }

    /** Handles one line. Returns false when the client wants to quit. */
    public boolean handle(String raw) {
        String line = raw.trim();
        if (line.isEmpty()) {
            return true;
        }
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toUpperCase();
        String rest = parts.length > 1 ? parts[1] : "";

        try {
            switch (command) {
                case Protocol.PING -> { }
                case Protocol.CREATE -> enterRoom(null, rest.trim());
                case Protocol.JOIN -> {
                    String[] args = rest.split("\\s+");
                    if (args.length < 2 || args[0].isEmpty()) {
                        throw new IllegalArgumentException("Usage: JOIN <room> <name>");
                    }
                    enterRoom(args[0], args[1]);
                }
                case Protocol.SET -> {
                    String[] kv = rest.split("\\s+", 2);
                    if (kv.length < 2) {
                        throw new IllegalArgumentException("Usage: SET <setting> <value>");
                    }
                    requireRoom().set(this, kv[0], kv[1]);
                }
                case Protocol.START -> requireRoom().start(this);
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

    /** Joins room {@code code}, or a brand-new room when code is null. */
    private void enterRoom(String code, String requestedName) {
        if (room != null) {
            throw new IllegalStateException("You are already in room " + room.id());
        }
        if (!NAME.matcher(requestedName).matches()) {
            throw new IllegalArgumentException("Names can use letters, numbers, - _ . and up to 16 characters");
        }
        name = requestedName;
        GameRoom target = code == null ? rooms.create() : rooms.getOrCreate(code);
        target.join(this);
        room = target;
    }

    private GameRoom requireRoom() {
        if (room == null) {
            throw new IllegalStateException("Join a room first");
        }
        return room;
    }

    public String name() {
        return name;
    }

    /** Thread-safe: rooms call this from timer threads and other players' threads. */
    public synchronized void send(String message) {
        if (closed) {
            return;
        }
        try {
            out.accept(message);
        } catch (RuntimeException e) {
            // The client went away mid-send; the transport will report the disconnect.
        }
    }

    /** Called once when the transport closes. Safe to call more than once. */
    public void disconnected() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        if (room != null) {
            room.leave(this);
        }
    }
}
