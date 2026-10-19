package com.big2.core.ai;

import com.big2.core.card.Card;
import com.big2.core.game.Game;
import com.big2.core.hand.Hand;
import com.big2.core.hand.MoveGenerator;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A beginner-level bot.
 *
 * <ul>
 *   <li>Leading: plays its lowest card, inside the biggest combination that contains it.</li>
 *   <li>Following: plays the weakest hand that beats the table, or passes.</li>
 * </ul>
 *
 * TODO (week 4): build a SmarterBot that saves 2s, avoids breaking up pairs,
 * and plays more aggressively when an opponent is close to winning.
 */
public final class SimpleBot implements Bot {

    @Override
    public Optional<Hand> chooseMove(Game game, int seat) {
        List<Hand> options = MoveGenerator.playableHands(
                game.player(seat).hand(), game.lastPlay(), game.isFirstMove());
        if (options.isEmpty()) {
            return Optional.empty();
        }

        Comparator<Hand> weakestFirst = Comparator
                .comparingInt((Hand h) -> h.type().ordinal())
                .thenComparing(Hand::keyCard);

        if (game.isLeading()) {
            // Get rid of the lowest card, inside the biggest combination that uses it.
            Card lowest = game.player(seat).hand().get(0);
            Comparator<Hand> lead = Comparator
                    .comparingInt(Hand::size).reversed()
                    .thenComparing(weakestFirst);
            return options.stream().filter(h -> h.contains(lowest)).min(lead);
        }
        return options.stream().min(weakestFirst);
    }
}
