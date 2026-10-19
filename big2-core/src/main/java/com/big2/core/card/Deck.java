package com.big2.core.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** A standard 52-card deck. */
public final class Deck {

    private final List<Card> cards = new ArrayList<>(52);

    public Deck() {
        for (Rank rank : Rank.values()) {
            for (Suit suit : Suit.values()) {
                cards.add(new Card(rank, suit));
            }
        }
    }

    public void shuffle(Random random) {
        Collections.shuffle(cards, random);
    }

    public int size() {
        return cards.size();
    }

    public List<Card> cards() {
        return Collections.unmodifiableList(cards);
    }

    /**
     * Deals the whole deck round-robin into {@code players} hands.
     * Each returned hand is sorted lowest to highest.
     */
    public List<List<Card>> deal(int players) {
        if (players <= 0 || cards.size() % players != 0) {
            throw new IllegalArgumentException("Cannot deal 52 cards evenly to " + players + " players");
        }
        List<List<Card>> hands = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            hands.add(new ArrayList<>());
        }
        for (int i = 0; i < cards.size(); i++) {
            hands.get(i % players).add(cards.get(i));
        }
        hands.forEach(Collections::sort);
        return hands;
    }
}
