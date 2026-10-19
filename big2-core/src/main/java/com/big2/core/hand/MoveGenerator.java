package com.big2.core.hand;

import com.big2.core.card.Card;
import com.big2.core.game.HouseRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists every legal {@link Hand} that can be formed from a player's cards.
 * Used by bots and for hints. With 13 cards there are at most C(13,5) = 1287
 * five-card subsets, so brute force is fast enough.
 */
public final class MoveGenerator {

    private MoveGenerator() {
    }

    public static List<Hand> allHands(List<Card> cards) {
        return allHands(cards, HouseRules.STANDARD);
    }

    public static List<Hand> allHands(List<Card> cards, HouseRules rules) {
        List<Hand> result = new ArrayList<>();
        int n = cards.size();
        for (int a = 0; a < n; a++) {
            add(result, List.of(cards.get(a)), rules);
            for (int b = a + 1; b < n; b++) {
                add(result, List.of(cards.get(a), cards.get(b)), rules);
                for (int c = b + 1; c < n; c++) {
                    add(result, List.of(cards.get(a), cards.get(b), cards.get(c)), rules);
                    for (int d = c + 1; d < n; d++) {
                        for (int e = d + 1; e < n; e++) {
                            add(result, List.of(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)), rules);
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Hands from {@code cards} that may legally be played on {@code toBeat} (null = free lead). */
    public static List<Hand> playableHands(List<Card> cards, Hand toBeat, boolean mustIncludeThreeOfDiamonds) {
        return playableHands(cards, toBeat, mustIncludeThreeOfDiamonds, HouseRules.STANDARD);
    }

    public static List<Hand> playableHands(List<Card> cards, Hand toBeat, boolean mustIncludeThreeOfDiamonds,
                                           HouseRules rules) {
        List<Hand> playable = new ArrayList<>();
        for (Hand h : allHands(cards, rules)) {
            if (mustIncludeThreeOfDiamonds && !h.contains(Card.THREE_OF_DIAMONDS)) {
                continue;
            }
            if (h.beats(toBeat)) {
                playable.add(h);
            }
        }
        return playable;
    }

    private static void add(List<Hand> result, List<Card> cards, HouseRules rules) {
        HandEvaluator.evaluate(cards, rules).ifPresent(result::add);
    }
}
