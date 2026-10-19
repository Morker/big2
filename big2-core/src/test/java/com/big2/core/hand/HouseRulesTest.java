package com.big2.core.hand;

import com.big2.core.card.Card;
import com.big2.core.game.HouseRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseRulesTest {

    private static final HouseRules TWOS = new HouseRules(true, false, true);
    private static final HouseRules WRAP = new HouseRules(false, true, true);

    private static Hand hand(String codes, HouseRules rules) {
        return HandEvaluator.evaluate(Card.parseAll(codes), rules)
                .orElseThrow(() -> new AssertionError("Expected a valid hand: " + codes));
    }

    private static boolean invalid(String codes, HouseRules rules) {
        return HandEvaluator.evaluate(Card.parseAll(codes), rules).isEmpty();
    }

    @Test
    void twosInStraightsAllowsJackToTwoAsTheTopStraight() {
        Hand top = hand("JD QC KH AS 2D", TWOS);
        assertEquals(HandType.STRAIGHT, top.type());
        assertTrue(top.beats(hand("10D JC QH KS AS", TWOS)));
        assertTrue(invalid("2D 3C 4H 5S 6D", TWOS), "wrap-around needs its own switch");
    }

    @Test
    void wrapStraightsAreTheLowestStraights() {
        Hand wheel = hand("AD 2C 3H 4S 5D", WRAP);
        Hand twoToSix = hand("2D 3C 4H 5S 6D", WRAP);
        Hand lowest = hand("3D 4C 5H 6S 7D", WRAP);
        assertEquals(Card.parse("5D"), wheel.keyCard());
        assertTrue(twoToSix.beats(wheel));
        assertTrue(lowest.beats(twoToSix));
        assertTrue(invalid("JD QC KH AS 2D", WRAP), "J-Q-K-A-2 needs the twos switch");
    }

    @Test
    void wrapStraightFlushIsStillAStraightFlush() {
        assertEquals(HandType.STRAIGHT_FLUSH, hand("AH 2H 3H 4H 5H", WRAP).type());
    }

    @Test
    void standardRulesAreUnchanged() {
        assertTrue(invalid("JD QC KH AS 2D", HouseRules.STANDARD));
        assertTrue(invalid("AD 2C 3H 4S 5D", HouseRules.STANDARD));
    }
}
