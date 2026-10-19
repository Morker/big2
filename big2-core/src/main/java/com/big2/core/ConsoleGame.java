package com.big2.core;

import com.big2.core.ai.Bot;
import com.big2.core.ai.SimpleBot;
import com.big2.core.card.Card;
import com.big2.core.game.Game;
import com.big2.core.game.InvalidMoveException;
import com.big2.core.game.Player;
import com.big2.core.hand.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;

/**
 * Play Big 2 in the terminal against three bots. Useful for testing the rules
 * before any UI exists (week 3 of the plan).
 *
 * <p>Enter card numbers separated by spaces to play (e.g. {@code 0 4}), or {@code p} to pass.
 */
public final class ConsoleGame {

    public static void main(String[] args) {
        Game game = new Game(List.of("You", "Bot Ali", "Bot Mei", "Bot Raj"), new Random());
        Bot bot = new SimpleBot();
        Scanner in = new Scanner(System.in);

        System.out.println("=== Big 2 ===  Type card numbers to play (e.g. '0 3'), or 'p' to pass.\n");

        while (!game.isOver()) {
            int seat = game.currentSeat();
            Player player = game.player(seat);

            if (seat != 0) {
                Optional<Hand> move = bot.takeTurn(game, seat);
                System.out.printf("%-8s %s%n", player.name() + ":",
                        move.map(Hand::toString).orElse("pass"));
                continue;
            }

            printTable(game);
            System.out.print("> ");
            if (!in.hasNextLine()) {
                return;
            }
            String line = in.nextLine().trim();
            try {
                if (line.equalsIgnoreCase("p")) {
                    game.pass(0);
                    System.out.println("You:     pass");
                } else {
                    Hand played = game.play(0, pick(player.hand(), line));
                    System.out.println("You:     " + played);
                }
            } catch (InvalidMoveException | IllegalArgumentException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }

        System.out.println("\n*** " + game.winner().name() + " wins! ***");
        System.out.println("Penalty points: " + game.penalties());
    }

    private static void printTable(Game game) {
        System.out.println();
        for (Player p : game.players()) {
            if (p != game.player(0)) {
                System.out.printf("  %s has %d cards%n", p.name(), p.cardCount());
            }
        }
        System.out.println("  Table: " + (game.isLeading() ? "(empty - you lead)" : game.lastPlay()));
        List<Card> hand = game.player(0).hand();
        StringBuilder sb = new StringBuilder("  Your hand: ");
        for (int i = 0; i < hand.size(); i++) {
            sb.append('[').append(i).append(']').append(hand.get(i).code()).append(' ');
        }
        System.out.println(sb);
    }

    private static List<Card> pick(List<Card> hand, String line) {
        List<Card> picked = new ArrayList<>();
        for (String token : line.split("\\s+")) {
            int index;
            try {
                index = Integer.parseInt(token);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a card number: " + token);
            }
            if (index < 0 || index >= hand.size()) {
                throw new IllegalArgumentException("No card number " + index);
            }
            picked.add(hand.get(index));
        }
        return picked;
    }
}
