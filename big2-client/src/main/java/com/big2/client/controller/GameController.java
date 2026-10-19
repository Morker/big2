package com.big2.client.controller;

import com.big2.core.card.Card;

import java.util.List;
import java.util.function.Consumer;

/**
 * What the UI can ask for. Two implementations:
 * <ul>
 *   <li>{@link LocalGameController}: runs the game in-process against bots.</li>
 *   <li>{@link RemoteGameController}: talks to big2-server over the network.</li>
 * </ul>
 * The UI depends only on this interface, so switching between them is one line.
 */
public interface GameController {

    /** Registers the callback that receives a new {@link TableState} after every change. */
    void setListener(Consumer<TableState> listener);

    void newGame();

    void play(List<Card> cards);

    void pass();

    /** Text for the button that calls {@link #newGame()}. */
    default String newGameLabel() {
        return "New game";
    }
}
