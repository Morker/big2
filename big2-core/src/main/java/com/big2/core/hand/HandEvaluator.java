package com.big2.core.hand;

import com.big2.core.card.Card;
import com.big2.core.card.Rank;
import com.big2.core.game.HouseRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a set of cards into a {@link Hand}, or reports that it is not a legal combination.
 *
 * <p>By default ({@link HouseRules#STANDARD}):
 * <ul>
 *   <li>Straights use consecutive ranks from 3-4-5-6-7 up to 10-J-Q-K-A; a 2 cannot be part of a straight.</li>
 *   <li>Straights and flushes are compared by their highest card (rank, then suit).</li>
 *   <li>Full houses compare the triple; four of a kind compares the four.</li>
 * </ul>
 * {@link HouseRules} can add J-Q-K-A-2 at the top and A-2-3-4-5 / 2-3-4-5-6 at the bottom.
 */
public final class HandEvaluator {

    private HandEvaluator() {
    }

    public static Optional<Hand> evaluate(Collection<Card> input) {
        return evaluate(input, HouseRules.STANDARD);
    }

    public static Optional<Hand> evaluate(Collection<Card> input, HouseRules rules) {
        List<Card> cards = new ArrayList<>(input);
        cards.sort(null);
        if (cards.stream().distinct().count() != cards.size()) {
            return Optional.empty();
        }
        return switch (cards.size()) {
            case 1 -> Optional.of(new Hand(HandType.SINGLE, cards, cards.get(0)));
            case 2 -> sameRank(cards) ? Optional.of(new Hand(HandType.PAIR, cards, highest(cards))) : Optional.empty();
            case 3 -> sameRank(cards) ? Optional.of(new Hand(HandType.TRIPLE, cards, highest(cards))) : Optional.empty();
            case 5 -> evaluateFive(cards, rules);
            default -> Optional.empty();
        };
    }

    private static Optional<Hand> evaluateFive(List<Card> cards, HouseRules rules) {
        boolean flush = cards.stream().map(Card::suit).distinct().count() == 1;
        Card straightKey = straightKey(cards, rules);
        boolean straight = straightKey != null;
        Card high = highest(cards);

        if (straight && flush) {
            return Optional.of(new Hand(HandType.STRAIGHT_FLUSH, cards, straightKey));
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
            return Optional.of(new Hand(HandType.STRAIGHT, cards, straightKey));
        }
        return Optional.empty();
    }

    /**
     * Returns the card that ranks a straight, or null if the cards are not one under {@code rules}.
     * Normal straights are ranked by their top card. The low wrap-around straights are ranked by
     * their 5 (A-2-3-4-5) or 6 (2-3-4-5-6), which puts them below 3-4-5-6-7.
     */
    private static Card straightKey(List<Card> sorted, HouseRules rules) {
        boolean consecutive = true;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).rank().ordinal() != sorted.get(i - 1).rank().ordinal() + 1) {
                consecutive = false;
                break;
            }
        }
        boolean hasTwo = sorted.stream().anyMatch(c -> c.rank() == Rank.TWO);
        if (consecutive) {
            // In Big 2 order the only consecutive run containing a 2 is J-Q-K-A-2.
            return hasTwo && !rules.twosInStraights() ? null : sorted.get(sorted.size() - 1);
        }
        if (rules.wrapStraights()) {
            Set<Rank> ranks = EnumSet.noneOf(Rank.class);
            sorted.forEach(c -> ranks.add(c.rank()));
            if (ranks.equals(EnumSet.of(Rank.ACE, Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE))) {
                return cardOfRank(sorted, Rank.FIVE);
            }
            if (ranks.equals(EnumSet.of(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX))) {
                return cardOfRank(sorted, Rank.SIX);
            }
        }
        return null;
    }

    private static Card cardOfRank(List<Card> cards, Rank rank) {
        return cards.stream().filter(c -> c.rank() == rank).findFirst().orElseThrow();
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
