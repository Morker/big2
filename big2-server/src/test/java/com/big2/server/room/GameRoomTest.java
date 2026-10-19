package com.big2.server.room;

import com.big2.core.card.Card;
import com.big2.core.game.HouseRules;
import com.big2.core.hand.Hand;
import com.big2.core.hand.HandEvaluator;
import com.big2.core.hand.MoveGenerator;
import com.big2.core.match.GameSettings;
import com.big2.server.PlayerSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives rooms through the real protocol, with scripted players standing in for browsers. */
class GameRoomTest {

    private static final RoomTiming FAST = new RoomTiming(5, 1);

    /** A player that reads protocol lines and answers its own turns with the weakest legal move. */
    private static final class Agent implements Runnable {
        final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        final PlayerSession session;
        int seat = -1;
        int deal;
        int playsThisDeal;
        List<Card> hand = new ArrayList<>();
        Hand table;
        HouseRules rules = HouseRules.STANDARD;
        volatile String matchLine;

        Agent(RoomManager rooms) {
            session = new PlayerSession(rooms, line -> {
                log.add(line);
                inbox.add(line);
            });
        }

        @Override
        public void run() {
            try {
                while (matchLine == null) {
                    String line = inbox.poll(30, TimeUnit.SECONDS);
                    if (line == null) {
                        return;
                    }
                    react(line);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void react(String line) {
            String[] p = line.split(" ", 3);
            switch (p[0]) {
                case "SEAT" -> seat = Integer.parseInt(p[1]);
                case "SETTINGS" -> rules = GameSettings.parse(line.substring("SETTINGS ".length())).rules();
                case "DEAL" -> {
                    deal = Integer.parseInt(p[1]);
                    playsThisDeal = 0;
                    table = null;
                }
                case "HAND" -> {
                    hand = new ArrayList<>(Card.parseAll(line.substring("HAND".length())));
                    Collections.sort(hand);
                }
                case "PLAYED" -> {
                    table = HandEvaluator.evaluate(Card.parseAll(p[2]), rules).orElseThrow();
                    playsThisDeal++;
                }
                case "TURN" -> {
                    boolean lead = p[2].startsWith("LEAD");
                    if (lead) {
                        table = null;
                    }
                    if (Integer.parseInt(p[1]) == seat) {
                        move(lead);
                    }
                }
                case "MATCH" -> matchLine = line;
                default -> { }
            }
        }

        private void move(boolean lead) {
            boolean mustOpen = playsThisDeal == 0 && hand.contains(Card.THREE_OF_DIAMONDS)
                    && (deal == 1 || rules.threeOfDiamondsEveryDeal());
            List<Hand> options = MoveGenerator.playableHands(hand, lead ? null : table, mustOpen, rules);
            if (options.isEmpty()) {
                session.handle("PASS");
                return;
            }
            Hand weakest = options.stream()
                    .min(Comparator.comparingInt((Hand h) -> h.type().ordinal()).thenComparing(Hand::keyCard))
                    .orElseThrow();
            session.handle("PLAY " + weakest.cards().stream().map(Card::code).collect(Collectors.joining(" ")));
        }

        long count(String prefix) {
            synchronized (log) {
                return log.stream().filter(l -> l.startsWith(prefix + " ")).count();
            }
        }

        String last(String prefix) {
            synchronized (log) {
                for (int i = log.size() - 1; i >= 0; i--) {
                    if (log.get(i).startsWith(prefix + " ")) {
                        return log.get(i);
                    }
                }
            }
            return null;
        }
    }

    private static String roomCode(Agent agent) {
        return agent.last("LOBBY").split(" ")[1];
    }

    @Test
    void twoPlayersAndTwoBotsFinishATwoDealMatch() throws Exception {
        RoomManager rooms = new RoomManager(FAST);
        Agent alice = new Agent(rooms);
        Agent bob = new Agent(rooms);
        Thread ta = Thread.ofVirtual().start(alice);
        Thread tb = Thread.ofVirtual().start(bob);

        alice.session.handle("CREATE Alice");
        bob.session.handle("JOIN " + roomCode(alice) + " Bob");
        alice.session.handle("SET mode rounds");
        alice.session.handle("SET rounds 2");
        alice.session.handle("SET timer 0");
        alice.session.handle("SET every3d off");
        alice.session.handle("START");

        ta.join(60_000);
        tb.join(60_000);

        assertNotNull(alice.matchLine, "match should finish");
        assertEquals(alice.matchLine, bob.matchLine);
        assertEquals(2, alice.count("WIN"));
        assertEquals(2, bob.count("SCORES"));
        assertEquals(1, alice.count("NEXT"), "one pause between the two deals");
        assertTrue(alice.last("SETTINGS").contains("mode=rounds"));
    }

    @Test
    void onlyTheOwnerChangesSettingsAndOwnershipPassesOn() {
        RoomManager rooms = new RoomManager(FAST);
        Agent alice = new Agent(rooms);
        Agent bob = new Agent(rooms);
        alice.session.handle("CREATE Alice");
        bob.session.handle("JOIN " + roomCode(alice) + " Bob");

        bob.session.handle("SET timer 60");
        assertTrue(bob.last("ERROR").contains("owner"));
        bob.session.handle("START");
        assertEquals("OWNER 0", alice.last("OWNER"));

        alice.session.disconnected();
        assertEquals("OWNER 1", bob.last("OWNER"));
        bob.session.handle("SET room library");
        assertTrue(bob.last("SETTINGS").contains("room=library deck=classic"));
    }

    @Test
    void duplicateNamesAreMadeUniqueAndEmptyRoomsAreRemoved() {
        RoomManager rooms = new RoomManager(FAST);
        Agent first = new Agent(rooms);
        Agent second = new Agent(rooms);
        first.session.handle("CREATE Sam");
        second.session.handle("JOIN " + roomCode(first) + " Sam");
        assertTrue(second.last("LOBBY").endsWith("Sam,Sam2,-,-"));

        first.session.disconnected();
        second.session.disconnected();
        assertEquals(0, rooms.size());
    }
}
