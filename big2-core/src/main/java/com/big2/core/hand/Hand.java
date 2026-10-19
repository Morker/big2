package com.big2.core.hand;

import com.big2.core.card.Card;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A valid combination of cards that can be played to the table.
 * Create instances with {@link HandEvaluator#evaluate}.
 *
 * @param type    the combination type
 * @param cards   the cards, sorted lowest to highest
 * @param keyCard the card that decides comparisons between hands of the same type
 *                (e.g. the highest card of the triple in a full house)
 */
public record Hand(HandType type, List<Card> cards, Card keyCard) {

    public Hand {
        cards = List.copyOf(cards);
    }

    public int size() {
        return cards.size();
    }

    /**
     * Returns true if this hand can be played on top of {@code other}.
     * A null {@code other} means the table is empty, so any hand is allowed.
     *
     * <p>Hands only beat hands with the same number of cards. Among five-card
     * hands, a stronger type always wins; otherwise the key card decides.
     */
    public boolean beats(Hand other) {
        if (other == null) {
            return true;
        }
        if (size() != other.size()) {
            return false;
        }
        if (size() == 5 && type != other.type) {
            return type.ordinal() > other.type.ordinal();
        }
        return keyCard.compareTo(other.keyCard) > 0;
    }

    public boolean contains(Card card) {
        return cards.contains(card);
    }

    @Override
    public String toString() {
        return type.displayName() + " " + cards.stream().map(Card::code).collect(Collectors.joining(" "));
    }
}
