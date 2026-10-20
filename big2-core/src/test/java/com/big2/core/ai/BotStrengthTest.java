package com.big2.core.ai;

import com.big2.core.game.Game;
import com.big2.core.game.HouseRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bot-against-bot tournaments. One "hero" bot plays against three copies of another level,
 * changing seats every deal; with equal strength the hero would win about 25% of deals.
 */
class BotStrengthTest {

    private static final List<String> NAMES = List.of("A", "B", "C", "D");
    private static final int DEALS = 1000;

    record Result(double winShare, double heroPenalty, double othersPenalty) {
        @Override
        public String toString() {
            return String.format("wins %.1f%%, avg penalty %.2f vs %.2f", winShare * 100, heroPenalty, othersPenalty);
        }
    }

    static Result duel(BotLevel hero, BotLevel others, int deals, HouseRules rules) {
        Bot heroBot = hero.create();
        Bot otherBot = others.create();
        int wins = 0;
        long heroPenalty = 0, othersPenalty = 0;
        for (int d = 0; d < deals; d++) {
            int heroSeat = d % Game.PLAYER_COUNT;
            Game game = new Game(NAMES, new Random(d), rules, Game.THREE_OF_DIAMONDS_LEADS);
            int turns = 0;
            while (!game.isOver()) {
                int seat = game.currentSeat();
                (seat == heroSeat ? heroBot : otherBot).takeTurn(game, seat);
                if (++turns > 2000) {
                    throw new AssertionError("deal " + d + " did not finish");
                }
            }
            if (game.winnerSeat() == heroSeat) {
                wins++;
            }
            for (int s = 0; s < Game.PLAYER_COUNT; s++) {
                if (s == heroSeat) {
                    heroPenalty += game.penalty(s);
                } else {
                    othersPenalty += game.penalty(s);
                }
            }
        }
        return new Result(wins / (double) deals, heroPenalty / (double) deals, othersPenalty / (3.0 * deals));
    }

    @Test
    void strongerLevelsWinMoreOften() {
        Result normalVsEasy = duel(BotLevel.NORMAL, BotLevel.EASY, DEALS, HouseRules.STANDARD);
        Result hardVsEasy = duel(BotLevel.HARD, BotLevel.EASY, DEALS, HouseRules.STANDARD);
        Result hardVsNormal = duel(BotLevel.HARD, BotLevel.NORMAL, DEALS, HouseRules.STANDARD);
        Result easyVsNormal = duel(BotLevel.EASY, BotLevel.NORMAL, DEALS, HouseRules.STANDARD);
        System.out.println("Normal vs 3 Easy:   " + normalVsEasy);
        System.out.println("Hard   vs 3 Easy:   " + hardVsEasy);
        System.out.println("Hard   vs 3 Normal: " + hardVsNormal);
        System.out.println("Easy   vs 3 Normal: " + easyVsNormal);

        assertTrue(normalVsEasy.winShare() > 0.30, "Normal should beat Easy: " + normalVsEasy);
        assertTrue(hardVsEasy.winShare() >= normalVsEasy.winShare() - 0.02, "Hard should do at least as well as Normal against Easy");
        assertTrue(hardVsNormal.winShare() > 0.25, "Hard should beat Normal: " + hardVsNormal);
        assertTrue(easyVsNormal.winShare() < 0.22, "Easy should lose to Normal: " + easyVsNormal);
    }

    @Test
    void everyLevelPlaysLegallyUnderEveryHouseRule() {
        for (boolean twos : new boolean[]{false, true}) {
            for (boolean wrap : new boolean[]{false, true}) {
                HouseRules rules = new HouseRules(twos, wrap, true);
                for (BotLevel level : BotLevel.values()) {
                    // Game throws InvalidMoveException on any illegal move, failing the test.
                    duel(level, BotLevel.EASY, 60, rules);
                }
            }
        }
    }
}
