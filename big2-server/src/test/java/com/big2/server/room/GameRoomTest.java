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

    private static final RoomTiming FAST = new RoomTiming(5, 1, 50, 300, 1500);

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
    void hostCanPlayAgainStraightAfterAGameEnds() throws Exception {
        RoomManager rooms = new RoomManager(FAST);
        Agent alice = new Agent(rooms);
        alice.session.handle("CREATE Alice");
        alice.session.handle("SET timer 0");
        alice.session.handle("SET bots hard");

        Thread first = Thread.ofVirtual().start(alice);
        alice.session.handle("START");
        first.join(60_000);
        String firstResult = alice.matchLine;
        assertNotNull(firstResult, "first game should finish");

        // Play again: no new room, no settings, just START.
        alice.matchLine = null;
        Thread second = Thread.ofVirtual().start(alice);
        alice.session.handle("START");
        second.join(60_000);

        assertNotNull(alice.matchLine, "second game should finish");
        assertEquals(2, alice.count("MATCH"));
        assertEquals(2, alice.count("DEAL"), "one deal per game in single mode");
        assertTrue(alice.last("SETTINGS").contains("bots=hard"), "settings carry over");
        assertEquals(0, alice.count("ERROR"));
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
    void reactionsReachEveryoneButSpamIsDropped() {
        RoomManager rooms = new RoomManager(FAST);
        Agent alice = new Agent(rooms);
        Agent bob = new Agent(rooms);
        alice.session.handle("CREATE Alice");
        bob.session.handle("JOIN " + roomCode(alice) + " Bob");

        bob.session.handle("REACT fire");
        bob.session.handle("REACT lol"); // too soon after the last one: dropped
        assertEquals("REACT 1 fire", alice.last("REACT"));
        assertEquals(1, alice.count("REACT"));

        alice.session.handle("REACT <script>");
        assertTrue(alice.last("ERROR").contains("Unknown reaction"));
        assertEquals(1, bob.count("REACT"), "nothing is sent for an unknown reaction");
    }

    @Test
    void duplicateNamesAreMadeUniqueAndEmptyRoomsAreRemoved() {
        RoomManager rooms = new RoomManager(FAST);
        Agent first = new Agent(rooms);
        Agent second = new Agent(rooms);
        first.session.handle("CREATE Sam");
        second.session.handle("JOIN " + roomCode(first) + " Sam");
        assertTrue(second.last("LOBBY").endsWith("Sam,Sam2,-,-"));

        // Leaving on purpose frees the room at once.
        first.session.handle("QUIT");
        second.session.handle("QUIT");
        assertEquals(0, rooms.size());
    }

    // ---- Reconnecting ----

    /** Waits up to two seconds for a condition that other threads (timers, bots) will make true. */
    private static void await(String what, java.util.function.BooleanSupplier condition) throws InterruptedException {
        long until = System.currentTimeMillis() + 2000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > until) {
                throw new AssertionError("Timed out waiting for: " + what);
            }
            Thread.sleep(10);
        }
    }

    private static String key(Agent agent) {
        return agent.last("TOKEN").split(" ")[1];
    }

    @Test
    void aDroppedPlayerKeepsTheirSeatAndCanComeBack() throws Exception {
        RoomManager rooms = new RoomManager(FAST);
        Agent alice = new Agent(rooms);
        Agent bob = new Agent(rooms);
        alice.session.handle("CREATE Alice");
        bob.session.handle("JOIN " + roomCode(alice) + " Bob");

        bob.session.disconnected();
        assertEquals("AWAY 1", alice.last("AWAY"));
        assertTrue(alice.last("LOBBY").endsWith("Alice,Bob,-,-"), "the seat is still Bob's");

        Agent bobAgain = new Agent(rooms);
        bobAgain.session.handle("REJOIN " + roomCode(alice) + " " + key(bob));
        assertEquals("SEAT 1", bobAgain.last("SEAT"));
        assertTrue(bobAgain.log.contains("RESUME") && bobAgain.log.contains("RESUMED"), "a snapshot is sent");
        assertEquals("BACK 1", alice.last("BACK"));

        // Long after the grace period, Bob is still seated because he came back.
        Thread.sleep(400);
        assertEquals(0, alice.count("LEFT"));
    }

    @Test
    void aSeatIsFreedWhenTheGracePeriodRunsOutOrThePlayerQuits() throws Exception {
        RoomManager rooms = new RoomManager(FAST);
        Agent alice = new Agent(rooms);
        Agent bob = new Agent(rooms);
        Agent carol = new Agent(rooms);
        alice.session.handle("CREATE Alice");
        bob.session.handle("JOIN " + roomCode(alice) + " Bob");
        carol.session.handle("JOIN " + roomCode(alice) + " Carol");

        bob.session.disconnected();
        await("Bob's seat to be released", () -> "LEFT 1".equals(alice.last("LEFT")));
        assertTrue(alice.last("LOBBY").endsWith("Alice,-,Carol,-"));

        carol.session.handle("QUIT");
        assertEquals("LEFT 2", alice.last("LEFT"), "quitting frees the seat at once");

        Agent late = new Agent(rooms);
        late.session.handle("REJOIN " + roomCode(alice) + " " + key(bob));
        assertTrue(late.last("ERROR").contains("given up"));
    }

    @Test
    void theTablePausesWhileEveryoneIsAwayAndPicksUpOnReturn() throws Exception {
        RoomManager rooms = new RoomManager(FAST);
        Agent alice = new Agent(rooms);
        alice.session.handle("CREATE Alice");
        alice.session.handle("SET timer 0");
        alice.session.handle("START");
        await("the deal", () -> alice.count("HAND") > 0);
        // Let the bots play until it is Alice's turn, then drop her connection.
        await("Alice's turn", () -> alice.last("TURN") != null && alice.last("TURN").startsWith("TURN 0 "));
        alice.session.disconnected();

        long movesBefore = alice.count("PLAYED") + alice.count("PASSED");
        Thread.sleep(300); // well past the 50 ms away-turn wait
        assertEquals(movesBefore, alice.count("PLAYED") + alice.count("PASSED"), "nothing happens while nobody is here");

        Agent back = new Agent(rooms);
        back.session.handle("REJOIN " + roomCode(alice) + " " + key(alice));
        List<String> snapshot = new ArrayList<>(back.log);
        int start = snapshot.indexOf("RESUME"), end = snapshot.indexOf("RESUMED");
        assertTrue(start >= 0 && end > start, "a snapshot is sent: " + snapshot);
        List<String> body = snapshot.subList(start, end);
        assertTrue(body.stream().anyMatch(l -> l.startsWith("HAND ")), "your cards");
        assertTrue(body.stream().anyMatch(l -> l.startsWith("COUNTS ")), "everyone's card counts");
        assertTrue(body.stream().anyMatch(l -> l.startsWith("TURN 0 ")), "whose turn it is");
        assertTrue(back.last("TURN").startsWith("TURN 0 "), "and it is still Alice's turn");
    }
}
