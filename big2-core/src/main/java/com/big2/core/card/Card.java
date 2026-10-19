package com.big2.core.card;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable playing card.
 *
 * <p>Cards are ordered by rank first, then suit, so 3♦ is the lowest card in the
 * deck and 2♠ is the highest.
 */
public record Card(Rank rank, Suit suit) implements Comparable<Card> {

    public static final Card THREE_OF_DIAMONDS = new Card(Rank.THREE, Suit.DIAMONDS);

    public Card {
        if (rank == null || suit == null) {
            throw new IllegalArgumentException("rank and suit are required");
        }
    }

    /** A unique strength from 0 (3♦) to 51 (2♠). */
    public int value() {
        return rank.ordinal() * Suit.values().length + suit.ordinal();
    }

    @Override
    public int compareTo(Card other) {
        return Integer.compare(value(), other.value());
    }

    /** Parses a short code such as "3D", "10S", "TS", "QH" or "2C". */
    public static Card parse(String code) {
        String s = code.trim();
        if (s.length() < 2) {
            throw new IllegalArgumentException("Bad card code: " + code);
        }
        Rank rank = Rank.fromLabel(s.substring(0, s.length() - 1));
        Suit suit = Suit.fromCode(s.charAt(s.length() - 1));
        return new Card(rank, suit);
    }

    /** Parses a space-separated list, e.g. "3D 3S 4H". */
    public static List<Card> parseAll(String codes) {
        List<Card> cards = new ArrayList<>();
        for (String part : codes.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                cards.add(parse(part));
            }
        }
        return cards;
    }

    /** Short ASCII code, e.g. "10S". Safe for consoles and the network protocol. */
    public String code() {
        return rank.label() + suit.code();
    }

    /** Display form with a suit symbol, e.g. "10♠". */
    public String pretty() {
        return rank.label() + suit.symbol();
    }

    @Override
    public String toString() {
        return code();
    }
}
