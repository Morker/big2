package com.big2.core.ai;

import com.big2.core.game.Game;
import com.big2.core.hand.Hand;

import java.util.Optional;

/** A computer player strategy. */
public interface Bot {

    /**
     * Chooses a move for the player in {@code seat}.
     *
     * @return the hand to play, or empty to pass
     */
    Optional<Hand> chooseMove(Game game, int seat);

    /** Convenience: asks the bot for a move and applies it to the game. */
    default Optional<Hand> takeTurn(Game game, int seat) {
        Optional<Hand> move = chooseMove(game, seat);
        if (move.isPresent()) {
            game.play(seat, move.get().cards());
        } else {
            game.pass(seat);
        }
        return move;
    }
}
