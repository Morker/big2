package com.big2.core.match;

import com.big2.core.ai.SimpleBot;
import com.big2.core.game.Game;
import com.big2.core.game.HouseRules;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchTest {

    private static final List<String> NAMES = List.of("A", "B", "C", "D");
    private static final SimpleBot BOT = new SimpleBot();

    private static void playOut(Game game) {
        while (!game.isOver()) {
            BOT.takeTurn(game, game.currentSeat());
        }
    }

    @Test
    void singleModeEndsAfterOneDeal() {
        Match match = new Match(GameSettings.DEFAULT);
        playOut(match.startDeal(NAMES, new Random(1)));
        match.finishDeal();
        assertTrue(match.isOver());
        assertThrows(IllegalStateException.class, () -> match.startDeal(NAMES, new Random(2)));
    }

    @Test
    void roundsModePlaysTheConfiguredNumberOfDeals() {
        Match match = new Match(GameSettings.DEFAULT.with("mode", "rounds").with("rounds", "3"));
        for (int deal = 0; deal < 3; deal++) {
            assertFalse(match.isOver());
            playOut(match.startDeal(NAMES, new Random(deal)));
            match.finishDeal();
        }
        assertTrue(match.isOver());
        assertEquals(3, match.dealsPlayed());
    }

    @Test
    void pointsModeEndsWhenSomeoneReachesTheLimit() {
        Match match = new Match(GameSettings.DEFAULT.with("mode", "points").with("points", "10"));
        int deals = 0;
        while (!match.isOver()) {
            playOut(match.startDeal(NAMES, new Random(deals)));
            match.finishDeal();
            assertTrue(++deals < 100);
        }
        assertTrue(Arrays.stream(match.scores()).anyMatch(s -> s >= 10));
    }

    @Test
    void winnerLeadsFreelyWhenThreeOfDiamondsOnlyOpensTheFirstDeal() {
        GameSettings settings = GameSettings.DEFAULT.with("mode", "rounds").with("every3d", "off");
        Match match = new Match(settings);
        playOut(match.startDeal(NAMES, new Random(5)));
        match.finishDeal();
        Game second = match.startDeal(NAMES, new Random(6));
        assertEquals(match.lastWinner(), second.currentSeat());
        assertFalse(second.isFirstMove());
    }

    @Test
    void settingsRoundTripThroughTheWireFormat() {
        GameSettings s = GameSettings.DEFAULT.with("room", "library").with("deck", "deco")
                .with("mode", "points").with("points", "80").with("timer", "45").with("wrap", "on");
        assertEquals(s, GameSettings.parse(s.toWire()));
        assertEquals("classic", GameSettings.DEFAULT.with("room", "library").deck(), "room picks its deck");
        assertEquals(new HouseRules(false, true, true), s.rules());
    }

    @Test
    void settingsRejectBadValues() {
        assertThrows(IllegalArgumentException.class, () -> GameSettings.DEFAULT.with("timer", "5"));
        assertThrows(IllegalArgumentException.class, () -> GameSettings.DEFAULT.with("room", "moon"));
        assertThrows(IllegalArgumentException.class, () -> GameSettings.DEFAULT.with("rounds", "0"));
    }
}
