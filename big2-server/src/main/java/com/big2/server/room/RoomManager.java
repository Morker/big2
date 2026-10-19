package com.big2.server.room;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps track of all game rooms by id. */
public final class RoomManager {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    public GameRoom getOrCreate(String id) {
        return rooms.computeIfAbsent(id, GameRoom::new);
    }

    // TODO: remove rooms when the last human leaves, so memory doesn't grow forever.
    public void remove(String id) {
        rooms.remove(id);
    }

    public int size() {
        return rooms.size();
    }
}
