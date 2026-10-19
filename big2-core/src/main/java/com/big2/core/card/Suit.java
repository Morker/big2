package com.big2.core.card;

/**
 * Card suits in Big 2 order, lowest to highest: Diamonds < Clubs < Hearts < Spades.
 * The declaration order matters: {@link #ordinal()} is used for comparison.
 */
public enum Suit {
    DIAMONDS('D', "♦", true),
    CLUBS('C', "♣", false),
    HEARTS('H', "♥", true),
    SPADES('S', "♠", false);

    private final char code;
    private final String symbol;
    private final boolean red;

    Suit(char code, String symbol, boolean red) {
        this.code = code;
        this.symbol = symbol;
        this.red = red;
    }

    /** Short ASCII code used for parsing and the network protocol, e.g. 'D'. */
    public char code() {
        return code;
    }

    /** Unicode symbol for display, e.g. "♦". */
    public String symbol() {
        return symbol;
    }

    public boolean isRed() {
        return red;
    }

    public static Suit fromCode(char c) {
        char upper = Character.toUpperCase(c);
        for (Suit s : values()) {
            if (s.code == upper) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown suit: " + c);
    }
}
