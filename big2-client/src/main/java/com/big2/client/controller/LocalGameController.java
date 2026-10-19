package com.big2.client.controller;

import com.big2.core.ai.Bot;
import com.big2.core.ai.SimpleBot;
import com.big2.core.card.Card;
import com.big2.core.game.Game;
import com.big2.core.game.InvalidMoveException;
import com.big2.core.game.Player;
import com.big2.core.hand.Hand;

import javax.swing.Timer;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Single-player mode: you sit in seat 0 and three {@link SimpleBot}s fill the rest.
 * Bots move one at a time on a Swing timer so you can follow what they play.
 */
public final class LocalGameController implements GameController {

    private static final int HUMAN_SEAT = 0;
    private static final int BOT_DELAY_MS = 700;
    private static final List<String> NAMES = List.of("You", "Ali", "Mei", "Raj");

    private final Bot bot = new SimpleBot();
    private final Timer botTimer = new Timer(BOT_DELAY_MS, e -> botStep());
    private Consumer<TableState> listener = s -> { };
    private Game game;
    private String message = "";

    @Override
    public void setListener(Consumer<TableState> listener) {
        this.listener = listener;
    }

    @Override
    public void newGame() {
        botTimer.stop();
        game = new Game(NAMES, new Random());
        message = game.currentSeat() == HUMAN_SEAT
                ? "You have the 3♦, so you start."
                : NAMES.get(game.currentSeat()) + " has the 3♦ and starts.";
        publish();
        scheduleBots();
    }

    @Override
    public void play(List<Card> cards) {
        try {
            Hand hand = game.play(HUMAN_SEAT, cards);
            message = "You played " + hand.type().displayName().toLowerCase() + ".";
        } catch (InvalidMoveException e) {
            message = e.getMessage();
        }
        publish();
        scheduleBots();
    }

    @Override
    public void pass() {
        try {
            game.pass(HUMAN_SEAT);
            message = "You passed.";
        } catch (InvalidMoveException e) {
            message = e.getMessage();
        }
        publish();
        scheduleBots();
    }

    private void scheduleBots() {
        if (!game.isOver() && game.currentSeat() != HUMAN_SEAT) {
            botTimer.start();
        }
    }

    private void botStep() {
        if (game.isOver() || game.currentSeat() == HUMAN_SEAT) {
            botTimer.stop();
            return;
        }
        int seat = game.currentSeat();
        Optional<Hand> move = bot.takeTurn(game, seat);
        String who = game.player(seat).name();
        message = move.map(h -> who + " played " + h.type().displayName().toLowerCase() + ".")
                .orElse(who + " passed.");
        publish();
    }

    private void publish() {
        List<Integer> counts = game.players().stream().map(Player::cardCount).toList();
        Hand table = game.lastPlay();
        int winner = game.isOver() ? game.players().indexOf(game.winner()) : -1;
        String result = winner == HUMAN_SEAT ? "You win!" : NAMES.get(winner < 0 ? 0 : winner) + " wins!";
        String text = winner >= 0 ? result + "  Penalties: " + game.penalties() : message;
        listener.accept(new TableState(
                NAMES,
                counts,
                HUMAN_SEAT,
                List.copyOf(game.player(HUMAN_SEAT).hand()),
                game.currentSeat(),
                table == null ? List.of() : table.cards(),
                table == null ? -1 : game.lastPlaySeat(),
                game.isLeading(),
                text,
                winner));
    }
}
