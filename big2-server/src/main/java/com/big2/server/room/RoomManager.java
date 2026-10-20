package com.big2.server.room;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Keeps track of all game rooms by code, and owns the scheduler rooms use for
 * bot pacing, turn timers and the pause between deals. Empty rooms are removed.
 */
public final class RoomManager {

    /** No I, L or O, so codes read cleanly when shared out loud. */
    private static final String CODE_LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 4;

    private final Map<String, GameRoom> rooms = new HashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1, Thread.ofVirtual().name("room-timer-", 0).factory());
    private final SecureRandom random = new SecureRandom();
    private final RoomTiming timing;

    public RoomManager() {
        this(RoomTiming.DEFAULT);
    }

    public RoomManager(RoomTiming timing) {
        this.timing = timing;
    }

    /** Returns the room with this code, creating it if needed. Codes are case-insensitive. */
    public synchronized GameRoom getOrCreate(String code) {
        String id = code.trim().toUpperCase();
        return rooms.computeIfAbsent(id, this::newRoom);
    }

    /** The room with this code, or null if there isn't one (it never existed or has closed). */
    public synchronized GameRoom find(String code) {
        return rooms.get(code.trim().toUpperCase());
    }

    /** Creates a room with a fresh, unused code. */
    public synchronized GameRoom create() {
        String id;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
            }
            id = sb.toString();
        } while (rooms.containsKey(id));
        GameRoom room = newRoom(id);
        rooms.put(id, room);
        return room;
    }

    public synchronized int size() {
        return rooms.size();
    }

    private GameRoom newRoom(String id) {
        return new GameRoom(id, scheduler, timing, this::removeIfSame);
    }

    private synchronized void removeIfSame(GameRoom room) {
        rooms.remove(room.id(), room);
    }
}
