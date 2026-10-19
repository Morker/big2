package com.big2.core.game;

import com.big2.core.ai.SimpleBot;
import com.big2.core.card.Card;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {

    private static final List<String> NAMES = List.of("A", "B", "C", "D");

    /** Small, hand-picked deals make the rules easy to test. */
    private static Game smallGame() {
        return new Game(NAMES, List.of(
                Card.parseAll("3D 5C"),
                Card.parseAll("4D 9H"),
                Card.parseAll("6S 7S"),
                Card.parseAll("KH 2S")));
    }

    @Test
    void holderOfThreeOfDiamondsStarts() {
        assertEquals(0, smallGame().currentSeat());
    }

    @Test
    void firstPlayMustIncludeThreeOfDiamonds() {
        Game game = smallGame();
        assertThrows(InvalidMoveException.class, () -> game.play(0, Card.parseAll("5C")));
        game.play(0, Card.parseAll("3D"));
        assertEquals(1, game.currentSeat());
    }

    @Test
    void cannotPlayOutOfTurn() {
        Game game = smallGame();
        assertThrows(InvalidMoveException.class, () -> game.play(1, Card.parseAll("4D")));
    }

    @Test
    void cannotPlayCardsYouDoNotHold() {
        Game game = smallGame();
        assertThrows(InvalidMoveException.class, () -> game.play(0, Card.parseAll("3D 3C")));
    }

    @Test
    void mustBeatTheTable() {
        Game game = new Game(NAMES, List.of(
                Card.parseAll("3D 4C"),
                Card.parseAll("3C 9H"),
                Card.parseAll("6S 7S"),
                Card.parseAll("5H 2S")));
        game.play(0, Card.parseAll("3D"));
        game.play(1, Card.parseAll("3C"));
        game.play(2, Card.parseAll("7S"));
        // 5♥ is lower than 7♠ on the table.
        assertThrows(InvalidMoveException.class, () -> game.play(3, Card.parseAll("5H")));
        // Two unrelated cards are not a valid combination.
        assertThrows(InvalidMoveException.class, () -> game.play(3, Card.parseAll("5H 2S")));
        game.play(3, Card.parseAll("2S"));
    }

    @Test
    void cannotPassWhenLeading() {
        Game game = smallGame();
        assertThrows(InvalidMoveException.class, () -> game.pass(0));
    }

    @Test
    void whenEveryoneElsePassesTheLastPlayerLeadsAgain() {
        Game game = smallGame();
        game.play(0, Card.parseAll("3D"));
        game.play(1, Card.parseAll("4D"));
        game.pass(2);
        game.pass(3);
        game.pass(0);

        assertEquals(1, game.currentSeat());
        assertTrue(game.isLeading());
    }

    @Test
    void emptyingYourHandWinsTheGame() {
        Game game = smallGame();
        game.play(0, Card.parseAll("3D"));
        game.play(1, Card.parseAll("4D"));
        game.pass(2);
        game.pass(3);
        game.pass(0);
        game.play(1, Card.parseAll("9H"));

        assertTrue(game.isOver());
        assertEquals("B", game.winner().name());
        assertThrows(InvalidMoveException.class, () -> game.pass(2));
    }

    @Test
    void botsCanFinishManyFullGames() {
        SimpleBot bot = new SimpleBot();
        for (int seed = 0; seed < 200; seed++) {
            Game game = new Game(NAMES, new Random(seed));
            int turns = 0;
            while (!game.isOver()) {
                bot.takeTurn(game, game.currentSeat());
                assertTrue(++turns < 1000, "game should end, seed " + seed);
            }
            assertNotNull(game.winner());
            assertEquals(0, game.winner().cardCount());
        }
    }
}
