package com.big2.core.card;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardTest {

    @Test
    void threeOfDiamondsIsLowestAndTwoOfSpadesIsHighest() {
        assertEquals(0, Card.parse("3D").value());
        assertEquals(51, Card.parse("2S").value());
    }

    @Test
    void rankBeatsSuit() {
        assertTrue(Card.parse("4D").compareTo(Card.parse("3S")) > 0);
        assertTrue(Card.parse("2D").compareTo(Card.parse("AS")) > 0);
    }

    @Test
    void suitOrderIsDiamondsClubsHeartsSpades() {
        assertTrue(Card.parse("7C").compareTo(Card.parse("7D")) > 0);
        assertTrue(Card.parse("7H").compareTo(Card.parse("7C")) > 0);
        assertTrue(Card.parse("7S").compareTo(Card.parse("7H")) > 0);
    }

    @Test
    void parsesTensBothWays() {
        assertEquals(Card.parse("10S"), Card.parse("TS"));
        assertEquals("10S", Card.parse("ts").code());
    }

    @Test
    void parseAllReadsAList() {
        assertEquals(List.of(Card.parse("3D"), Card.parse("QH")), Card.parseAll(" 3D  QH "));
    }

    @Test
    void rejectsBadCodes() {
        assertThrows(IllegalArgumentException.class, () -> Card.parse("1D"));
        assertThrows(IllegalArgumentException.class, () -> Card.parse("3X"));
        assertThrows(IllegalArgumentException.class, () -> Card.parse("D"));
    }

    @Test
    void deckHas52UniqueCardsDealtEvenly() {
        Deck deck = new Deck();
        deck.shuffle(new Random(42));
        assertEquals(52, new HashSet<>(deck.cards()).size());

        List<List<Card>> hands = deck.deal(4);
        assertEquals(4, hands.size());
        hands.forEach(h -> assertEquals(13, h.size()));
    }
}
