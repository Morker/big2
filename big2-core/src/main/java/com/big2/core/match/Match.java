package com.big2.core.match;

import com.big2.core.game.Game;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A series of deals with running penalty totals, ended according to {@link GameSettings.Mode}.
 *
 * <p>Usage: {@link #startDeal}, play the returned {@link Game} until it is over,
 * {@link #finishDeal}, and repeat while {@link #isOver()} is false.
 */
public final class Match {

    private final GameSettings settings;
    private final int[] scores = new int[Game.PLAYER_COUNT];
    private final int[] lastPenalties = new int[Game.PLAYER_COUNT];
    private int dealsPlayed;
    private int lastWinner = -1;
    private Game game;

    public Match(GameSettings settings) {
        this.settings = settings;
    }

    /**
     * Deals a new hand. Names are passed per deal because a seat can change hands
     * between deals (for example when a bot takes over for someone who left).
     */
    public Game startDeal(List<String> names, Random random) {
        if (isOver()) {
            throw new IllegalStateException("The match is over");
        }
        if (game != null && !game.isOver()) {
            throw new IllegalStateException("The current deal is still being played");
        }
        boolean threeOfDiamondsLeads = dealsPlayed == 0 || settings.rules().threeOfDiamondsEveryDeal();
        game = new Game(names, random, settings.rules(), threeOfDiamondsLeads ? Game.THREE_OF_DIAMONDS_LEADS : lastWinner);
        return game;
    }

    /** Scores the finished deal. */
    public void finishDeal() {
        if (game == null || !game.isOver()) {
            throw new IllegalStateException("No finished deal to score");
        }
        for (int seat = 0; seat < scores.length; seat++) {
            lastPenalties[seat] = game.penalty(seat);
            scores[seat] += lastPenalties[seat];
        }
        lastWinner = game.winnerSeat();
        dealsPlayed++;
    }

    public boolean isOver() {
        return switch (settings.mode()) {
            case SINGLE -> dealsPlayed >= 1;
            case ROUNDS -> dealsPlayed >= settings.rounds();
            case POINTS -> Arrays.stream(scores).anyMatch(s -> s >= settings.pointLimit());
        };
    }

    /** The seat with the fewest penalty points; ties go to the lower seat number. */
    public int leaderSeat() {
        int best = 0;
        for (int seat = 1; seat < scores.length; seat++) {
            if (scores[seat] < scores[best]) {
                best = seat;
            }
        }
        return best;
    }

    public GameSettings settings() {
        return settings;
    }

    /** The deal in progress or just finished, or null before the first deal. */
    public Game game() {
        return game;
    }

    public int[] scores() {
        return scores.clone();
    }

    public int[] lastPenalties() {
        return lastPenalties.clone();
    }

    public int dealsPlayed() {
        return dealsPlayed;
    }

    /** Winner of the most recent finished deal, or -1. */
    public int lastWinner() {
        return lastWinner;
    }
}
