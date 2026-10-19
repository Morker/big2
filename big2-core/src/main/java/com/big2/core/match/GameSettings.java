package com.big2.core.match;

import com.big2.core.game.HouseRules;

import java.util.List;

/**
 * Everything the room owner chooses before a match: the look of the table and the rules of play.
 * Immutable; {@link #with} returns a changed copy and rejects bad values with a readable message.
 *
 * <p>On the wire it is one line of {@code key=value} pairs, see {@link #toWire()}.
 *
 * @param room        room design id, one of {@link #ROOMS}
 * @param deck        card design id, one of {@link #DECKS}
 * @param mode        how the match ends
 * @param pointLimit  {@link Mode#POINTS}: the match ends when someone reaches this many penalty points
 * @param rounds      {@link Mode#ROUNDS}: the number of deals to play
 * @param turnSeconds seconds a human gets per turn, or 0 for no timer
 * @param rules       house rule switches
 */
public record GameSettings(String room, String deck, Mode mode, int pointLimit, int rounds,
                           int turnSeconds, HouseRules rules) {

    public enum Mode {
        /** One deal; first to empty their hand wins. */
        SINGLE,
        /** Deals until someone reaches the point limit; lowest total wins. */
        POINTS,
        /** A fixed number of deals; lowest total wins. */
        ROUNDS;

        public String id() {
            return name().toLowerCase();
        }
    }

    public static final List<String> ROOMS = List.of("gold-room", "library", "salon", "tea-house", "studio");
    /** Each room's matching deck, in the same order as {@link #ROOMS}. */
    public static final List<String> DECKS = List.of("noir", "classic", "deco", "teahouse", "blueprint");

    public static final int MIN_POINTS = 10;
    public static final int MAX_POINTS = 200;
    public static final int MAX_ROUNDS = 20;
    public static final int MIN_TIMER = 10;
    public static final int MAX_TIMER = 120;

    public static final GameSettings DEFAULT =
            new GameSettings("gold-room", "noir", Mode.SINGLE, 50, 5, 30, HouseRules.STANDARD);

    /** The deck that matches a room. */
    public static String pairedDeck(String room) {
        int i = ROOMS.indexOf(room);
        return i < 0 ? DECKS.get(0) : DECKS.get(i);
    }

    /**
     * Returns a copy with one setting changed. Choosing a room also switches to its matching deck;
     * set {@code deck} afterwards to mix them.
     */
    public GameSettings with(String key, String value) {
        String v = value == null ? "" : value.trim().toLowerCase();
        HouseRules r = rules;
        return switch (key.toLowerCase()) {
            case "room" -> {
                if (!ROOMS.contains(v)) {
                    throw new IllegalArgumentException("Unknown room: " + value);
                }
                yield new GameSettings(v, pairedDeck(v), mode, pointLimit, rounds, turnSeconds, rules);
            }
            case "deck" -> {
                if (!DECKS.contains(v)) {
                    throw new IllegalArgumentException("Unknown deck: " + value);
                }
                yield new GameSettings(room, v, mode, pointLimit, rounds, turnSeconds, rules);
            }
            case "mode" -> new GameSettings(room, deck, parseMode(v), pointLimit, rounds, turnSeconds, rules);
            case "points" -> new GameSettings(room, deck, mode,
                    number(v, MIN_POINTS, MAX_POINTS, "Point limit"), rounds, turnSeconds, rules);
            case "rounds" -> new GameSettings(room, deck, mode, pointLimit,
                    number(v, 1, MAX_ROUNDS, "Rounds"), turnSeconds, rules);
            case "timer" -> {
                int seconds = number(v, 0, MAX_TIMER, "Turn timer");
                if (seconds != 0 && seconds < MIN_TIMER) {
                    throw new IllegalArgumentException("Turn timer must be 0 (off) or at least " + MIN_TIMER + " seconds");
                }
                yield new GameSettings(room, deck, mode, pointLimit, rounds, seconds, rules);
            }
            case "twos" -> new GameSettings(room, deck, mode, pointLimit, rounds, turnSeconds,
                    new HouseRules(flag(v), r.wrapStraights(), r.threeOfDiamondsEveryDeal()));
            case "wrap" -> new GameSettings(room, deck, mode, pointLimit, rounds, turnSeconds,
                    new HouseRules(r.twosInStraights(), flag(v), r.threeOfDiamondsEveryDeal()));
            case "every3d" -> new GameSettings(room, deck, mode, pointLimit, rounds, turnSeconds,
                    new HouseRules(r.twosInStraights(), r.wrapStraights(), flag(v)));
            default -> throw new IllegalArgumentException("Unknown setting: " + key);
        };
    }

    /** e.g. {@code room=gold-room deck=noir mode=points points=50 rounds=5 timer=30 twos=off wrap=off every3d=on} */
    public String toWire() {
        return "room=" + room
                + " deck=" + deck
                + " mode=" + mode.id()
                + " points=" + pointLimit
                + " rounds=" + rounds
                + " timer=" + turnSeconds
                + " twos=" + onOff(rules.twosInStraights())
                + " wrap=" + onOff(rules.wrapStraights())
                + " every3d=" + onOff(rules.threeOfDiamondsEveryDeal());
    }

    /** Reads {@link #toWire()} output. Keys must appear as room before deck, which toWire guarantees. */
    public static GameSettings parse(String wire) {
        GameSettings s = DEFAULT;
        for (String pair : wire.trim().split("\\s+")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                s = s.with(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return s;
    }

    private static Mode parseMode(String v) {
        for (Mode m : Mode.values()) {
            if (m.id().equals(v)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Mode must be single, points or rounds");
    }

    private static int number(String v, int min, int max, String what) {
        int n;
        try {
            n = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a number");
        }
        if (n < min || n > max) {
            throw new IllegalArgumentException(what + " must be between " + min + " and " + max);
        }
        return n;
    }

    private static boolean flag(String v) {
        return switch (v) {
            case "on", "true", "1", "yes" -> true;
            case "off", "false", "0", "no" -> false;
            default -> throw new IllegalArgumentException("Use on or off");
        };
    }

    private static String onOff(boolean b) {
        return b ? "on" : "off";
    }
}
