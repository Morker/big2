# Big 2 (Choi Dai Di) in Java

A multiplayer Big 2 card game: pure-Java rules engine, an authoritative game server, and a desktop client you can play against bots today.

> Replace this line with a gameplay GIF once the UI is polished.

## Project structure

```
big2/
├── pom.xml                      Parent Maven build (Java 21)
├── big2-core/                   Game rules — no UI, no networking
│   └── src/main/java/com/big2/core/
│       ├── card/                Suit, Rank, Card, Deck
│       ├── hand/                HandType, Hand, HandEvaluator, MoveGenerator
│       ├── game/                Game (the rules engine), Player, InvalidMoveException
│       ├── ai/                  Bot interface, SimpleBot
│       └── ConsoleGame.java     Play in the terminal vs 3 bots
│   └── src/test/java/...        JUnit 5 tests for cards, hands and game flow
├── big2-server/                 Multiplayer server (plain JDK sockets + virtual threads)
│   └── src/main/java/com/big2/server/
│       ├── Big2Server.java      Entry point, accepts connections
│       ├── ClientConnection.java  Parses one player's commands
│       ├── protocol/Protocol.java The text protocol, documented
│       └── room/                RoomManager, GameRoom (server-side game + bots)
└── big2-client/                 Desktop client (Swing)
    └── src/main/java/com/big2/client/
        ├── Big2ClientApp.java   Entry point
        ├── controller/          GameController, LocalGameController, RemoteGameController, TableState
        └── view/                GameWindow, CardView (cards drawn with Java2D)
```

### Why three modules?

- **core** holds every rule and is fully unit-tested. Both the server and the client reuse it.
- **server** is *authoritative*: it holds the deck, validates every move with `Game`, and sends each player only their own hand, so a modified client can't cheat.
- **client** only displays a `TableState` and forwards clicks. It talks to a `GameController`, so switching from local bots to online play doesn't touch the UI.

## Running it

Requirements: **JDK 21+** and **Maven 3.9+** (or just open the folder in IntelliJ IDEA, which imports Maven projects automatically).

```bash
# Build everything and run the tests
mvn clean install

# Desktop game vs 3 bots
mvn -pl big2-client exec:java

# Terminal game vs 3 bots
mvn -pl big2-core exec:java

# Multiplayer server on port 5050
mvn -pl big2-server exec:java
```

### Playing with a friend

1. **One person** starts the server: `mvn -pl big2-server exec:java` (allow it through the Windows firewall when asked).
2. **Both** run the client (`mvn -pl big2-client exec:java`), choose **Play with Friends**, and enter:
   - the host's address: `localhost` for the host, the host's IP for the friend
     (same Wi-Fi: the IPv4 address from `ipconfig`; over the internet: a Tailscale/ZeroTier address, or forward TCP port 5050 on the router),
   - port `5050`, and the same room name.
3. When both are in the waiting room, press **Start game**. Empty seats are filled by bots.

You can also talk to the server without the client:

```
nc localhost 5050          # or: telnet localhost 5050
JOIN room1 yourname
START                      # empty seats are filled with bots
PLAY 3D
PASS
```

## Rules implemented

- 52 cards, 4 players, 13 cards each.
- Ranks: 3 < 4 < … < K < A < 2. Suits: ♦ < ♣ < ♥ < ♠.
- The holder of 3♦ starts, and the first play must include it.
- Legal plays: single, pair, triple, and five-card hands (straight < flush < full house < four of a kind < straight flush).
- You must beat the table with the same number of cards, or pass. When all others pass, the last player leads anything.
- First to empty their hand wins. Penalty = cards left (×2 with 10+, ×3 with 13).
- 2s can't be used in straights. House rules vary by region, so making them configurable is on the roadmap.

## Roadmap

- [x] Week 1 – Cards, deck, dealing
- [x] Week 2 – Hand evaluation and comparison (+ tests)
- [x] Week 3 – Game engine and console game
- [x] Week 4 – SimpleBot
- [ ] Week 4 – SmarterBot (save 2s, keep pairs, react when an opponent is low on cards)
- [x] Week 5 – Desktop client (basic)
- [x] Week 6 – Server with rooms, bots filling empty seats
- [ ] Week 6 – `RemoteGameController`: parse server messages so the client can play online
- [ ] Week 7 – Reconnection, turn timer, scoring across rounds, chat
- [ ] Week 8 – Configurable house rules, card artwork, deploy the server, demo GIF

## Design decisions

Write a short paragraph for each of these as you go — interviewers love this section:

- Why the server is authoritative, and what that prevents.
- Why `Game` throws `InvalidMoveException` instead of returning booleans.
- Why a text protocol first (easy to test with `nc`) and what you'd change for a browser client.
- Why virtual threads for one-thread-per-connection.
