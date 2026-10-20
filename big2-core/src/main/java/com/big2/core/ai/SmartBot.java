package com.big2.core.ai;

import com.big2.core.card.Card;
import com.big2.core.card.Deck;
import com.big2.core.card.Rank;
import com.big2.core.game.Game;
import com.big2.core.game.HouseRules;
import com.big2.core.hand.Hand;
import com.big2.core.hand.MoveGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A bot that plans its hand instead of just playing the weakest card it can.
 *
 * <p>Both levels:
 * <ul>
 *   <li>split the hand into plays with {@link HandPlan} and avoid breaking those combinations;</li>
 *   <li>lead the play that holds their lowest card, so weak cards leave early;</li>
 *   <li>keep 2s and top pairs back while the game is young, instead of spending them on small cards;</li>
 *   <li>react when an opponent is nearly out: beat whatever they play, and avoid leading what they can follow.</li>
 * </ul>
 * The hard level also counts cards (every played card is public), so it knows when a play can't be
 * beaten, and uses that to keep control through the last few plays of its hand.
 */
public final class SmartBot implements Bot {

    private static final Comparator<Hand> WEAKEST_FIRST = Comparator
            .comparingInt((Hand h) -> h.type().ordinal())
            .thenComparing(Hand::keyCard);

    private final boolean countsCards;

    /** @param countsCards true for the hard level */
    public SmartBot(boolean countsCards) {
        this.countsCards = countsCards;
    }

    @Override
    public Optional<Hand> chooseMove(Game game, int seat) {
        List<Card> hand = game.player(seat).hand();
        HouseRules rules = game.rules();
        HandPlan plan = HandPlan.best(hand, rules);
        Set<Card> unseen = countsCards ? unseen(game, seat) : null;
        return game.isLeading()
                ? Optional.of(lead(game, seat, hand, plan, unseen))
                : follow(game, seat, hand, plan, unseen);
    }

    // ---- Leading ----

    private Hand lead(Game game, int seat, List<Card> hand, HandPlan plan, Set<Card> unseen) {
        HouseRules rules = game.rules();
        if (game.isFirstMove()) {
            return openWithThreeOfDiamonds(hand, plan, rules);
        }
        List<Hand> units = plan.units();
        if (units.size() == 1) {
            return units.get(0); // this play empties the hand
        }

        if (countsCards) {
            // Endgame: if every play but one is unbeatable, cash them in and finish with the last.
            List<Hand> safe = units.stream().filter(u -> unbeatable(u, game, seat, unseen)).toList();
            if (!safe.isEmpty() && safe.size() >= units.size() - 1) {
                return safe.stream().max(Comparator.comparingInt(Hand::size)).orElseThrow();
            }
        }

        int fewest = fewestOpponentCards(game, seat);
        if (fewest <= 3 && units.size() > 2) {
            // Someone is about to go out: shed as many cards as we can, with plays they can't follow.
            Optional<Hand> big = units.stream().filter(u -> u.size() > fewest)
                    .max(Comparator.comparingInt(Hand::size).thenComparing(Hand::keyCard, Comparator.reverseOrder()));
            if (big.isPresent()) {
                return big.get();
            }
        }
        if (fewest == 1) {
            // A single would let them go out: lead anything bigger, or our highest single.
            Optional<Hand> multi = units.stream().filter(u -> u.size() > 1).min(WEAKEST_FIRST);
            return multi.orElseGet(() -> units.stream().max(Comparator.comparing(Hand::keyCard)).orElseThrow());
        }
        if (fewest == 2) {
            Optional<Hand> notPair = units.stream().filter(u -> u.size() != 2).min(Comparator
                    .comparingInt((Hand u) -> u.size() == 1 ? 1 : 0) // prefer fives and triples
                    .thenComparing(Hand::keyCard));
            if (notPair.isPresent() && notPair.get().size() > 1) {
                return notPair.get();
            }
        }

        // Normally: get rid of the play holding our lowest card, keeping strong plays for later.
        Card lowest = hand.get(0);
        Hand withLowest = units.stream().filter(u -> u.contains(lowest)).findFirst().orElseThrow();
        if (!HandPlan.isControl(withLowest)) {
            return withLowest;
        }
        return units.stream().filter(u -> !HandPlan.isControl(u)).min(Comparator.comparing(Hand::keyCard))
                .orElse(withLowest);
    }

    private Hand openWithThreeOfDiamonds(List<Card> hand, HandPlan plan, HouseRules rules) {
        for (Hand u : plan.units()) {
            if (u.contains(Card.THREE_OF_DIAMONDS)) {
                return u;
            }
        }
        // Not possible with the plan as is (can't happen with a full plan, but stay safe).
        return MoveGenerator.playableHands(hand, null, true, rules).stream()
                .min(Comparator.comparingInt((Hand h) -> HandPlan.costWithout(hand, h.cards(), rules)))
                .orElseThrow();
    }

    // ---- Following ----

    private Optional<Hand> follow(Game game, int seat, List<Card> hand, HandPlan plan, Set<Card> unseen) {
        HouseRules rules = game.rules();
        Hand table = game.lastPlay();
        List<Hand> options = MoveGenerator.playableHands(hand, table, false, rules);
        if (options.isEmpty()) {
            return Optional.empty();
        }
        // Once anyone is close to going out, stop saving cards: every card still held is a penalty point.
        boolean urgent = game.player(game.lastPlaySeat()).cardCount() <= 2 || fewestOpponentCards(game, seat) <= 3;

        // Prefer a play from the plan, so no combination gets broken.
        Optional<Hand> fromPlan = plan.units().stream()
                .filter(u -> u.size() == table.size() && u.beats(table))
                .min(WEAKEST_FIRST);
        if (fromPlan.isPresent()) {
            Hand u = fromPlan.get();
            if (plan.units().size() == 1 || urgent || !worthHolding(u, table, plan, game, seat, unseen)) {
                return fromPlan;
            }
            return Optional.empty();
        }

        // Only breaking a combination would beat the table: do it when it costs little, or when we must.
        List<Hand> cheapest = options.stream().sorted(WEAKEST_FIRST).limit(8).toList();
        Hand best = null;
        int bestCost = Integer.MAX_VALUE;
        for (Hand h : cheapest) {
            int cost = HandPlan.costWithout(hand, h.cards(), rules);
            if (cost < bestCost) {
                best = h;
                bestCost = cost;
            }
        }
        boolean affordable = bestCost <= plan.cost() - 60 && !HandPlan.isControl(best);
        return urgent || affordable ? Optional.of(best) : Optional.empty();
    }

    /**
     * Whether to keep a strong play back instead of spending it now. Early in a deal, a 2 or a top
     * pair is worth more as a way to win back the lead later than as an answer to a small card.
     */
    private boolean worthHolding(Hand play, Hand table, HandPlan plan, Game game, int seat, Set<Card> unseen) {
        int playsLeft = plan.units().size();
        // The hard level knows which plays can't be beaten (an ace once all 2s are gone, say) and
        // treats them like 2s: a guaranteed way to win the lead back later.
        boolean strong = HandPlan.isControl(play) || (countsCards && unbeatable(play, game, seat, unseen));
        if (!strong || playsLeft <= 3) {
            return false;
        }
        if (countsCards && playsLeft <= 4 && unbeatable(play, game, seat, unseen)) {
            return false; // taking the lead now sets up the finish
        }
        // Spend it on strong cards (an ace or better single, a high pair); keep it against small ones.
        return table.keyCard().rank().ordinal() < Rank.ACE.ordinal();
    }

    // ---- Card counting ----

    private static Set<Card> unseen(Game game, int seat) {
        Set<Card> unseen = new HashSet<>(new Deck().cards());
        game.playedCards().forEach(unseen::remove);
        game.player(seat).hand().forEach(unseen::remove);
        return unseen;
    }

    /**
     * True if no opponent is expected to beat this play: either the cards still out there can't form
     * anything stronger, or each opponent who could answer has already passed on something at least
     * as strong, or holds too few cards.
     */
    private static boolean unbeatable(Hand play, Game game, int seat, Set<Card> unseen) {
        if (countingProves(play, unseen)) {
            return true;
        }
        for (int s = 0; s < Game.PLAYER_COUNT; s++) {
            if (s == seat || game.player(s).cardCount() < play.size()) {
                continue;
            }
            Hand passed = game.passedOn(s, play.size());
            if (passed == null || passed.beats(play)) {
                return false;
            }
        }
        return true;
    }

    /** True if the unseen cards can't form anything of the same size that beats this play. */
    private static boolean countingProves(Hand play, Set<Card> unseen) {
        switch (play.size()) {
            case 1:
                return unseen.stream().noneMatch(c -> c.compareTo(play.keyCard()) > 0);
            case 2:
            case 3: {
                Map<Rank, List<Card>> byRank = new EnumMap<>(Rank.class);
                unseen.forEach(c -> byRank.computeIfAbsent(c.rank(), k -> new ArrayList<>()).add(c));
                for (List<Card> group : byRank.values()) {
                    if (group.size() >= play.size()) {
                        Card top = group.stream().max(Card::compareTo).orElseThrow();
                        if (top.compareTo(play.keyCard()) > 0) {
                            return false;
                        }
                    }
                }
                return true;
            }
            default:
                return false; // five-card hands are too many to rule out cheaply
        }
    }

    private static int fewestOpponentCards(Game game, int seat) {
        int fewest = Integer.MAX_VALUE;
        for (int s = 0; s < Game.PLAYER_COUNT; s++) {
            if (s != seat) {
                fewest = Math.min(fewest, game.player(s).cardCount());
            }
        }
        return fewest;
    }
}
