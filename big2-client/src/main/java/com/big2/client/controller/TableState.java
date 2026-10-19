package com.big2.client.controller;

import com.big2.core.card.Card;

import java.util.List;

/**
 * Everything the UI needs to draw one frame of the table. Immutable, so it can be
 * built on any thread and handed to the Swing thread safely.
 *
 * <p>Both the local and the network controller produce this same object, which is
 * why the UI does not care where the game is actually running.
 *
 * @param names        player names by seat
 * @param cardCounts   cards left by seat
 * @param mySeat       which seat this client controls
 * @param myHand       this client's cards, sorted
 * @param currentSeat  whose turn it is
 * @param tableCards   the cards on the table (empty when the current player leads)
 * @param tableOwner   seat that played {@code tableCards}, or -1
 * @param leading      true if the current player may play anything
 * @param message      a status line, e.g. the last action or an error
 * @param winnerSeat   the winner's seat, or -1 while the game is running
 */
public record TableState(
        List<String> names,
        List<Integer> cardCounts,
        int mySeat,
        List<Card> myHand,
        int currentSeat,
        List<Card> tableCards,
        int tableOwner,
        boolean leading,
        String message,
        int winnerSeat) {

    public boolean myTurn() {
        return winnerSeat < 0 && currentSeat == mySeat;
    }

    public boolean over() {
        return winnerSeat >= 0;
    }
}
