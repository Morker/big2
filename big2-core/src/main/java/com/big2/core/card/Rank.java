package com.big2.core.card;

/**
 * Card ranks in Big 2 order, lowest to highest: 3 4 5 6 7 8 9 10 J Q K A 2.
 * The declaration order matters: {@link #ordinal()} is used for comparison.
 */
public enum Rank {
    THREE("3"), FOUR("4"), FIVE("5"), SIX("6"), SEVEN("7"), EIGHT("8"), NINE("9"),
    TEN("10"), JACK("J"), QUEEN("Q"), KING("K"), ACE("A"), TWO("2");

    private final String label;

    Rank(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Accepts "3".."10", "T" for ten, and "J", "Q", "K", "A", "2" (case-insensitive). */
    public static Rank fromLabel(String s) {
        String upper = s.toUpperCase();
        if (upper.equals("T")) {
            return TEN;
        }
        for (Rank r : values()) {
            if (r.label.equals(upper)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown rank: " + s);
    }
}
