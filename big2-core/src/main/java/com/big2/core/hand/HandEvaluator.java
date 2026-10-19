package com.big2.core.hand;

import com.big2.core.card.Card;
import com.big2.core.card.Rank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a set of cards into a {@link Hand}, or reports that it is not a legal combination.
 *
 * <p>House rules vary between regions. This implementation uses one common set:
 * <ul>
 *   <li>Straights use consecutive ranks from 3-4-5-6-7 up to 10-J-Q-K-A; a 2 cannot be part of a straight.</li>
 *   <li>Straights and flushes are compared by their highest card (rank, then suit).</li>
 *   <li>Full houses compare the triple; four of a kind compares the four.</li>
 * </ul>
 * TODO: make these configurable through a HouseRules object.
 */
public final class HandEvaluator {

    private HandEvaluator() {
    }

    public static Optional<Hand> evaluate(Collection<Card> input) {
        List<Card> cards = new ArrayList<>(input);
        cards.sort(null);
        if (cards.stream().distinct().count() != cards.size()) {
            return Optional.empty();
        }
        return switch (cards.size()) {
            case 1 -> Optional.of(new Hand(HandType.SINGLE, cards, cards.get(0)));
            case 2 -> sameRank(cards) ? Optional.of(new Hand(HandType.PAIR, cards, highest(cards))) : Optional.empty();
            case 3 -> sameRank(cards) ? Optional.of(new Hand(HandType.TRIPLE, cards, highest(cards))) : Optional.empty();
            case 5 -> evaluateFive(cards);
            default -> Optional.empty();
        };
    }

    private static Optional<Hand> evaluateFive(List<Card> cards) {
        boolean flush = cards.stream().map(Card::suit).distinct().count() == 1;
        boolean straight = isStraight(cards);
        Card high = highest(cards);

        if (straight && flush) {
            return Optional.of(new Hand(HandType.STRAIGHT_FLUSH, cards, high));
        }

        Map<Rank, List<Card>> byRank = groupByRank(cards);
        for (List<Card> group : byRank.values()) {
            if (group.size() == 4) {
                return Optional.of(new Hand(HandType.FOUR_OF_A_KIND, cards, highest(group)));
            }
        }
        if (byRank.size() == 2) {
            // Two rank groups and no four-of-a-kind means 3 + 2.
            for (List<Card> group : byRank.values()) {
                if (group.size() == 3) {
                    return Optional.of(new Hand(HandType.FULL_HOUSE, cards, highest(group)));
                }
            }
        }
        if (flush) {
            return Optional.of(new Hand(HandType.FLUSH, cards, high));
        }
        if (straight) {
            return Optional.of(new Hand(HandType.STRAIGHT, cards, high));
        }
        return Optional.empty();
    }

    private static boolean isStraight(List<Card> sorted) {
        for (int i = 0; i < sorted.size(); i++) {
            Rank r = sorted.get(i).rank();
            if (r == Rank.TWO) {
                return false;
            }
            if (i > 0 && r.ordinal() != sorted.get(i - 1).rank().ordinal() + 1) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameRank(List<Card> cards) {
        return cards.stream().map(Card::rank).distinct().count() == 1;
    }

    private static Card highest(List<Card> cards) {
        return cards.stream().max(Card::compareTo).orElseThrow();
    }

    private static Map<Rank, List<Card>> groupByRank(List<Card> cards) {
        Map<Rank, List<Card>> map = new EnumMap<>(Rank.class);
        for (Card c : cards) {
            map.computeIfAbsent(c.rank(), k -> new ArrayList<>()).add(c);
        }
        return map;
    }
}
