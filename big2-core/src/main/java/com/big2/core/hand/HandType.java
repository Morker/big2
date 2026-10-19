package com.big2.core.hand;

/**
 * The legal combinations in Big 2.
 *
 * <p>For five-card hands the declaration order is also the strength order:
 * straight &lt; flush &lt; full house &lt; four of a kind &lt; straight flush.
 */
public enum HandType {
    SINGLE(1),
    PAIR(2),
    TRIPLE(3),
    STRAIGHT(5),
    FLUSH(5),
    FULL_HOUSE(5),
    FOUR_OF_A_KIND(5),
    STRAIGHT_FLUSH(5);

    private final int size;

    HandType(int size) {
        this.size = size;
    }

    public int size() {
        return size;
    }

    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
    }
}
