package com.big2.core.hand;

import com.big2.core.card.Card;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEvaluatorTest {

    private static Hand hand(String codes) {
        return HandEvaluator.evaluate(Card.parseAll(codes))
                .orElseThrow(() -> new AssertionError("Expected a valid hand: " + codes));
    }

    private static boolean invalid(String codes) {
        return HandEvaluator.evaluate(Card.parseAll(codes)).isEmpty();
    }

    // ---- Recognition ----

    @Test
    void recognisesEveryHandType() {
        assertEquals(HandType.SINGLE, hand("9H").type());
        assertEquals(HandType.PAIR, hand("9H 9S").type());
        assertEquals(HandType.TRIPLE, hand("9H 9S 9D").type());
        assertEquals(HandType.STRAIGHT, hand("3D 4C 5H 6S 7D").type());
        assertEquals(HandType.FLUSH, hand("3H 8H 10H QH 2H").type());
        assertEquals(HandType.FULL_HOUSE, hand("KD KC KH 4S 4D").type());
        assertEquals(HandType.FOUR_OF_A_KIND, hand("8D 8C 8H 8S 3D").type());
        assertEquals(HandType.STRAIGHT_FLUSH, hand("5S 6S 7S 8S 9S").type());
    }

    @Test
    void orderOfInputDoesNotMatter() {
        assertEquals(HandType.STRAIGHT, hand("7D 3D 6S 4C 5H").type());
    }

    @Test
    void rejectsInvalidCombinations() {
        assertTrue(invalid("9H 10H"), "pair needs same rank");
        assertTrue(invalid("9H 9S 10D"), "triple needs same rank");
        assertTrue(invalid("3D 4D 5D 6D"), "four cards is never valid");
        assertTrue(invalid("3D 4C 5H 6S 8D"), "gap in straight");
        assertTrue(invalid("9H 9H"), "duplicate cards");
    }

    @Test
    void twoCannotBePartOfAStraight() {
        assertTrue(invalid("JD QC KH AS 2D"));
        assertTrue(invalid("2D 3C 4H 5S 6D"));
    }

    @Test
    void highestStraightIsTenToAce() {
        assertEquals(HandType.STRAIGHT, hand("10D JC QH KS AD").type());
    }

    // ---- Comparison ----

    @Test
    void anyHandBeatsAnEmptyTable() {
        assertTrue(hand("3D").beats(null));
    }

    @Test
    void singlesCompareRankThenSuit() {
        assertTrue(hand("2D").beats(hand("AS")));
        assertTrue(hand("5S").beats(hand("5H")));
        assertFalse(hand("5H").beats(hand("5S")));
    }

    @Test
    void pairsCompareTheirHighestCard() {
        assertTrue(hand("9D 9S").beats(hand("9C 9H")), "9♠ beats 9♥");
        assertTrue(hand("10D 10C").beats(hand("9H 9S")));
    }

    @Test
    void handsOnlyBeatHandsOfTheSameSize() {
        assertFalse(hand("2S").beats(hand("3D 3C")));
        assertFalse(hand("3D 3C").beats(hand("2S")));
    }

    @Test
    void fiveCardTypeOrderIsRespected() {
        Hand straight = hand("10D JC QH KS AD");
        Hand flush = hand("3C 5C 7C 9C JC");
        Hand fullHouse = hand("3D 3C 3H 4S 4D");
        Hand quads = hand("4D 4C 4H 4S 3S");
        Hand straightFlush = hand("3H 4H 5H 6H 7H");

        assertTrue(flush.beats(straight));
        assertTrue(fullHouse.beats(flush));
        assertTrue(quads.beats(fullHouse));
        assertTrue(straightFlush.beats(quads));
        assertFalse(straight.beats(straightFlush));
    }

    @Test
    void fullHouseIsComparedByTheTriple() {
        assertTrue(hand("5D 5C 5H 3S 3D").beats(hand("4D 4C 4H AS AD")));
    }

    @Test
    void straightsCompareTheirHighestCard() {
        assertTrue(hand("4D 5C 6H 7S 8D").beats(hand("3D 4C 5H 6S 7S")));
        assertTrue(hand("3C 4C 5H 6S 7S").beats(hand("3D 4D 5D 6D 7H")), "7♠ beats 7♥");
    }

    @Test
    void keyCardIsTheTripleInAFullHouse() {
        Optional<Hand> h = HandEvaluator.evaluate(Card.parseAll("3D 3C 3H 2S 2D"));
        assertEquals(Card.parse("3H"), h.orElseThrow().keyCard());
    }
}
