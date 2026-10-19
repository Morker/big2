package com.big2.core.game;

import com.big2.core.card.Card;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** A seat at the table and the cards currently held. */
public final class Player {

    private final String name;
    private final List<Card> hand = new ArrayList<>();

    public Player(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** The cards in hand, sorted lowest to highest. Read-only view. */
    public List<Card> hand() {
        return Collections.unmodifiableList(hand);
    }

    public int cardCount() {
        return hand.size();
    }

    public boolean hasAll(Collection<Card> cards) {
        return hand.containsAll(cards);
    }

    void receive(List<Card> cards) {
        hand.clear();
        hand.addAll(cards);
        Collections.sort(hand);
    }

    void remove(Collection<Card> cards) {
        hand.removeAll(cards);
    }

    @Override
    public String toString() {
        return name;
    }
}
