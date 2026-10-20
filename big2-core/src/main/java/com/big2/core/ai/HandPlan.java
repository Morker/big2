package com.big2.core.ai;

import com.big2.core.card.Card;
import com.big2.core.card.Rank;
import com.big2.core.game.HouseRules;
import com.big2.core.hand.Hand;
import com.big2.core.hand.HandEvaluator;
import com.big2.core.hand.HandType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A way to split a hand into the plays ("units") it will be emptied with, e.g.
 * one straight, two pairs and three singles.
 *
 * <p>{@link #best} searches for the split with the lowest {@link #cost()}: as few plays as possible,
 * then as few weak singles and pairs as possible, since low cards left on their own are the
 * hardest to get rid of. It tries up to two five-card hands and groups the rest by rank.
 */
public final class HandPlan {

    /** How many five-card hands to consider; the ones that use the lowest cards are tried first. */
    private static final int MAX_FIVES = 24;

    private final List<Hand> units;
    private final int cost;

    private HandPlan(List<Hand> units, int cost) {
        this.units = units;
        this.cost = cost;
    }

    /** The plays, weakest first within each size. */
    public List<Hand> units() {
        return units;
    }

    /** Lower is better: 100 per play, plus a penalty for each low single, pair or triple. */
    public int cost() {
        return cost;
    }

    public static HandPlan best(List<Card> cards, HouseRules rules) {
        List<Card> sorted = new ArrayList<>(cards);
        Collections.sort(sorted);
        List<Hand> fives = fiveCardHands(sorted, rules);

        HandPlan best = groupRest(sorted, List.of());
        for (int i = 0; i < fives.size(); i++) {
            Hand a = fives.get(i);
            List<Card> afterA = minus(sorted, a.cards());
            best = better(best, groupRest(afterA, List.of(a)));
            for (int j = i + 1; j < fives.size(); j++) {
                Hand b = fives.get(j);
                if (afterA.containsAll(b.cards())) {
                    best = better(best, groupRest(minus(afterA, b.cards()), List.of(a, b)));
                }
            }
        }
        return best;
    }

    /** The cost of the best plan for {@code cards} without {@code removed}. */
    public static int costWithout(List<Card> cards, List<Card> removed, HouseRules rules) {
        return best(minus(cards, removed), rules).cost;
    }

    private static List<Hand> fiveCardHands(List<Card> cards, HouseRules rules) {
        List<Hand> result = new ArrayList<>();
        int n = cards.size();
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                for (int c = b + 1; c < n; c++) {
                    for (int d = c + 1; d < n; d++) {
                        for (int e = d + 1; e < n; e++) {
                            HandEvaluator.evaluate(List.of(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)), rules)
                                    .ifPresent(result::add);
                        }
                    }
                }
            }
        }
        // Five-card hands that clear away low cards are the most useful.
        result.sort(Comparator.comparingInt((Hand h) -> h.cards().stream().mapToInt(c -> c.rank().ordinal()).sum())
                .thenComparing(Hand::keyCard));
        return result.size() > MAX_FIVES ? result.subList(0, MAX_FIVES) : result;
    }

    /** Groups the cards left after the chosen five-card hands into triples, pairs and singles. */
    private static HandPlan groupRest(List<Card> rest, List<Hand> fives) {
        List<Hand> units = new ArrayList<>(fives);
        Map<Rank, List<Card>> byRank = new EnumMap<>(Rank.class);
        for (Card c : rest) {
            byRank.computeIfAbsent(c.rank(), k -> new ArrayList<>()).add(c);
        }
        int penalty = 0;
        for (Map.Entry<Rank, List<Card>> entry : byRank.entrySet()) {
            List<Card> group = entry.getValue();
            int low = Rank.TWO.ordinal() - entry.getKey().ordinal(); // 0 for 2s, 12 for 3s
            switch (group.size()) {
                case 1 -> {
                    units.add(hand(group));
                    penalty += low * 3;
                }
                case 2 -> {
                    units.add(hand(group));
                    penalty += low;
                }
                case 3 -> {
                    units.add(hand(group));
                    penalty += low / 2;
                }
                default -> { // four of a kind left without a fifth card: play as two pairs
                    units.add(hand(group.subList(0, 2)));
                    units.add(hand(group.subList(2, 4)));
                    penalty += low * 2;
                }
            }
        }
        units.sort(Comparator.comparingInt(Hand::size).thenComparing(Hand::keyCard));
        return new HandPlan(units, units.size() * 100 + penalty);
    }

    private static Hand hand(List<Card> cards) {
        return HandEvaluator.evaluate(cards).orElseThrow();
    }

    private static HandPlan better(HandPlan a, HandPlan b) {
        return b.cost < a.cost ? b : a;
    }

    static List<Card> minus(List<Card> cards, List<Card> removed) {
        List<Card> rest = new ArrayList<>(cards);
        rest.removeAll(removed);
        return rest;
    }

    /** True for plays built from the very top cards (2s, or aces in pairs and up). */
    static boolean isControl(Hand h) {
        Rank r = h.keyCard().rank();
        return r == Rank.TWO || (h.size() > 1 && h.type() != HandType.STRAIGHT && r == Rank.ACE);
    }
}
