package com.big2.core.game;

import com.big2.core.card.Card;
import com.big2.core.card.Deck;
import com.big2.core.hand.Hand;
import com.big2.core.hand.HandEvaluator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * One deal of Big 2 for four players.
 *
 * <p>This class is the single source of truth for the rules: the server and
 * clients call {@link #play} and {@link #pass}, and every illegal action is
 * rejected with an {@link InvalidMoveException}. It holds no UI or network code.
 *
 * <p>Flow:
 * <ol>
 *   <li>The player holding 3♦ starts, and the first hand must include 3♦.</li>
 *   <li>Each player must beat the hand on the table with the same number of cards, or pass.</li>
 *   <li>When everyone else passes, the last player to play leads a fresh trick with any hand.</li>
 *   <li>The first player to empty their hand wins.</li>
 * </ol>
 */
public final class Game {

    public static final int PLAYER_COUNT = 4;

    private final List<Player> players;
    private int currentSeat;
    private Hand lastPlay;
    private int lastPlaySeat = -1;
    private int passesInRow;
    private boolean firstMove = true;
    private Player winner;

    /** Starts a game with a freshly shuffled deck. */
    public Game(List<String> names, Random random) {
        this(names, shuffledHands(random));
    }

    /** Starts a game with pre-dealt hands. Handy for tests and replaying games. */
    public Game(List<String> names, List<List<Card>> hands) {
        if (names.size() != PLAYER_COUNT || hands.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException("Big 2 needs exactly " + PLAYER_COUNT + " players");
        }
        List<Player> list = new ArrayList<>();
        for (String name : names) {
            list.add(new Player(name));
        }
        this.players = Collections.unmodifiableList(list);

        currentSeat = -1;
        for (int i = 0; i < PLAYER_COUNT; i++) {
            players.get(i).receive(hands.get(i));
            if (hands.get(i).contains(Card.THREE_OF_DIAMONDS)) {
                currentSeat = i;
            }
        }
        if (currentSeat < 0) {
            throw new IllegalArgumentException("Someone must hold the 3 of diamonds");
        }
    }

    private static List<List<Card>> shuffledHands(Random random) {
        Deck deck = new Deck();
        deck.shuffle(random);
        return deck.deal(PLAYER_COUNT);
    }

    /** Plays {@code cards} for the player in {@code seat}. Returns the hand that was played. */
    public Hand play(int seat, Collection<Card> cards) {
        checkTurn(seat);
        Player player = players.get(seat);

        if (cards.isEmpty()) {
            throw new InvalidMoveException("Select at least one card");
        }
        if (!player.hasAll(cards)) {
            throw new InvalidMoveException("You don't hold those cards");
        }
        Hand hand = HandEvaluator.evaluate(cards)
                .orElseThrow(() -> new InvalidMoveException("That is not a valid combination"));
        if (firstMove && !hand.contains(Card.THREE_OF_DIAMONDS)) {
            throw new InvalidMoveException("The first play must include the 3 of diamonds");
        }
        if (lastPlay != null && hand.size() != lastPlay.size()) {
            throw new InvalidMoveException("You must play " + lastPlay.size() + " card(s)");
        }
        if (!hand.beats(lastPlay)) {
            throw new InvalidMoveException(hand + " does not beat " + lastPlay);
        }

        player.remove(hand.cards());
        lastPlay = hand;
        lastPlaySeat = seat;
        passesInRow = 0;
        firstMove = false;

        if (player.cardCount() == 0) {
            winner = player;
        } else {
            advance();
        }
        return hand;
    }

    /** Passes for the player in {@code seat}. */
    public void pass(int seat) {
        checkTurn(seat);
        if (isLeading()) {
            throw new InvalidMoveException("You are leading, so you can't pass");
        }
        passesInRow++;
        if (passesInRow == PLAYER_COUNT - 1) {
            // Everyone else passed: the trick is won and the last player leads again.
            currentSeat = lastPlaySeat;
            lastPlay = null;
            passesInRow = 0;
        } else {
            advance();
        }
    }

    /**
     * Penalty points for each player once the game is over: one point per card left,
     * doubled with 10+ cards and tripled with all 13. A common scoring rule; adjust to taste.
     */
    public Map<String, Integer> penalties() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Player p : players) {
            int n = p.cardCount();
            int multiplier = n == 13 ? 3 : n >= 10 ? 2 : 1;
            result.put(p.name(), n * multiplier);
        }
        return result;
    }

    // ---- Read-only state, used by bots, the server and the UI ----

    public List<Player> players() {
        return players;
    }

    public Player player(int seat) {
        return players.get(seat);
    }

    public int currentSeat() {
        return currentSeat;
    }

    /** The hand currently on the table, or null when the current player is leading. */
    public Hand lastPlay() {
        return lastPlay;
    }

    public int lastPlaySeat() {
        return lastPlaySeat;
    }

    public boolean isLeading() {
        return lastPlay == null;
    }

    public boolean isFirstMove() {
        return firstMove;
    }

    public boolean isOver() {
        return winner != null;
    }

    public Player winner() {
        return winner;
    }

    private void checkTurn(int seat) {
        if (isOver()) {
            throw new InvalidMoveException("The game is over");
        }
        if (seat != currentSeat) {
            throw new InvalidMoveException("It is " + players.get(currentSeat).name() + "'s turn");
        }
    }

    private void advance() {
        currentSeat = (currentSeat + 1) % PLAYER_COUNT;
    }
}
