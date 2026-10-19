# Big 2 (Choi Dai Di) in Java

A multiplayer Big 2 card game you play in the browser: a pure-Java rules engine, an authoritative game server, and a web client with five table rooms and five card decks. The room owner picks the room, cards, game mode, turn timer and house rules before each game.

> Replace this line with a gameplay GIF once the UI is polished.

## Project structure

```
big2/
├── pom.xml                      Parent Maven build (Java 21)
├── big2-core/                   Game rules — no UI, no networking
│   └── src/main/java/com/big2/core/
│       ├── card/                Suit, Rank, Card, Deck
│       ├── hand/                HandType, Hand, HandEvaluator, MoveGenerator
│       ├── game/                Game (one deal), HouseRules, Player, InvalidMoveException
│       ├── match/               GameSettings (owner's choices), Match (deals + running scores)
│       ├── ai/                  Bot interface, SimpleBot
│       └── ConsoleGame.java     Play in the terminal vs 3 bots
│   └── src/test/java/...        JUnit 5 tests for cards, hands, house rules, games and matches
├── big2-server/                 Game server + web client (Javalin: HTTP, WebSocket; virtual threads)
│   ├── src/main/java/com/big2/server/
│   │   ├── Big2Server.java      Entry point: web server + optional TCP listener
│   │   ├── WebServer.java       Serves the web client and the /ws WebSocket
│   │   ├── PlayerSession.java   Parses one player's commands, for any transport
│   │   ├── ClientConnection.java  Plain TCP transport (for testing with nc)
│   │   ├── protocol/Protocol.java The text protocol, documented
│   │   └── room/                RoomManager, GameRoom (owner, settings, timers, bots, scoring)
│   └── src/main/resources/public/   The web client (no build step)
│       ├── index.html, css/app.css
│       └── js/cards.js, js/rooms.js, js/app.js
└── big2-client/                 Legacy desktop client (Swing), local play vs bots
```

### Why this split?

- **core** holds every rule and is fully unit-tested. The server and both clients reuse it.
- **server** is *authoritative*: it holds the deck, validates every move with `Game`, and sends each player only their own hand, so a modified client can't cheat. It also runs the bots, the turn timers and the scoring.
- **web client** only draws what the server tells it and sends clicks back. Cards and rooms are SVG drawn in code, so there are no image files.

## Running it

Requirements: **JDK 21+** and **Maven 3.9+** (or open the folder in IntelliJ IDEA, which imports Maven projects automatically).

```bash
# Build everything, run the tests, and package big2-server/target/big2-server.jar
mvn clean install

# Start the server, then open http://localhost:8090
java -jar big2-server/target/big2-server.jar
```

Options: `--http=PORT` (default 8090, or the `PORT` environment variable), `--tcp=PORT` (default 5050, `0` turns it off), and `--static=big2-server/src/main/resources/public` to serve the web files from source so edits show on reload.

Other ways to play:

```bash
# Terminal game vs 3 bots
mvn -pl big2-core exec:java

# Legacy desktop client vs 3 bots
mvn -pl big2-client exec:java
```

## Playing with friends

1. Start the server on your PC (allow Java through the Windows firewall when asked).
2. Open `http://localhost:8090`, enter your name and press **Create a room**.
3. Send your friends the **invite link** from the lobby. They open it, type a name and press **Join**.
4. As the host you choose the room, cards, game mode, turn timer and house rules, then press **Start game**. Empty seats are played by bots.

Your friends need to be able to reach your PC:

- **Same Wi-Fi:** they use your PC's IPv4 address from `ipconfig`, e.g. `http://192.168.1.20:8090/#KQTR`.
- **Over the internet**, pick one:
  - [Tailscale](https://tailscale.com): everyone installs it once and uses your Tailscale address. Private and simple.
  - A quick tunnel such as `cloudflared tunnel --url http://localhost:8090`, which gives you a public `https://…` link to share while it runs.
  - Forward TCP port 8090 on your router to your PC.
- **Host it online:** `big2-server.jar` runs on any host with Java 21 and reads the `PORT` environment variable, so it works on services like Render, Railway or Fly.io.

## Game settings (room owner)

| Setting | Options |
|---|---|
| Room | The Gold Room, The Library, Salon Privé, Tea House, Drafting Studio |
| Cards | Noir & Gold, Classic Parlour, Riviera Deco, Tea House, Blueprint (each room picks its matching deck, which you can change) |
| Game mode | **Single deal**; **Points race**: deals until someone reaches the limit (10–200), lowest total wins; **Fixed rounds**: 1–20 deals, lowest total wins |
| Turn timer | Off, or 10–120 seconds. When it runs out the player passes, or plays their lowest card if they must lead |
| House rules | 3♦ opens every deal (or only the first, then the last winner leads); 2s in straights (J-Q-K-A-2); low wrap-around straights (A-2-3-4-5, 2-3-4-5-6) |

## Rules implemented

- 52 cards, 4 players, 13 cards each.
- Ranks: 3 < 4 < … < K < A < 2. Suits: ♦ < ♣ < ♥ < ♠.
- The holder of 3♦ starts, and the first play must include it.
- Legal plays: single, pair, triple, and five-card hands (straight < flush < full house < four of a kind < straight flush).
- You must beat the table with the same number of cards, or pass. When all others pass, the last player leads anything.
- First to empty their hand wins the deal. Penalty = cards left (×2 with 10+, ×3 with 13).
- By default 2s can't be used in straights; the house rules above change that.

## Talking to the server directly

The same protocol runs over WebSocket (`/ws`) and plain TCP, so you can test it by hand:

```
nc localhost 5050          # or: telnet localhost 5050
CREATE yourname            # or: JOIN KQTR yourname
SET mode rounds
SET rounds 3
START                      # empty seats are filled with bots
PLAY 3D
PASS
```

See `Protocol.java` for every message.

## Roadmap

- [x] Week 1 – Cards, deck, dealing
- [x] Week 2 – Hand evaluation and comparison (+ tests)
- [x] Week 3 – Game engine and console game
- [x] Week 4 – SimpleBot
- [ ] Week 4 – SmarterBot (save 2s, keep pairs, react when an opponent is low on cards)
- [x] Week 5 – Desktop client (basic)
- [x] Week 6 – Server with rooms, bots filling empty seats
- [x] Web client with lobby, room owner settings, five rooms and five decks
- [x] Turn timer, scoring across deals, configurable house rules
- [ ] Reconnect to your seat after a dropped connection
- [ ] Portrait layout for phones, chat, sound
- [ ] Deploy the server, demo GIF

## Design decisions

Write a short paragraph for each of these as you go — interviewers love this section:

- Why the server is authoritative, and what that prevents.
- Why `Game` throws `InvalidMoveException` instead of returning booleans.
- Why one text protocol for both WebSocket and TCP (easy to test with `nc`).
- Why virtual threads, and why every room change happens under the room's lock with token-checked timers.
