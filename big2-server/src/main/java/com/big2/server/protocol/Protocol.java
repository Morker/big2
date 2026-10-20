package com.big2.server.protocol;

/**
 * The wire protocol: one UTF-8 text line per message, words separated by spaces.
 * The same lines travel over WebSocket (one line per message, used by the web client)
 * and plain TCP (one line per newline, handy for testing with nc).
 * Cards use short codes such as {@code 3D}, {@code 10S}, {@code QH}.
 *
 * <h2>Client to server</h2>
 * <pre>
 * CREATE &lt;name&gt;           create a room with a fresh code and join it as owner
 * JOIN &lt;room&gt; &lt;name&gt;     join (or create) a room by code
 * REJOIN &lt;room&gt; &lt;key&gt;    take your seat back after a dropped connection (key from TOKEN)
 * SET &lt;key&gt; &lt;value&gt;      owner only, between games: room, deck, mode, points, rounds,
 *                         timer, twos, wrap, every3d (see GameSettings)
 * START                   owner only: start the match; empty seats are filled with bots
 * PLAY &lt;card&gt; [card...]   play cards, e.g. PLAY 3D 3S
 * PASS                    pass
 * REACT &lt;id&gt;              send a reaction: like, lol, wow, cool, cry, angry, fire, clap, gg
 * PING                    keep-alive, ignored
 * QUIT                    leave on purpose; your seat goes to a bot (or is freed in the lobby)
 * </pre>
 *
 * <h2>Server to client</h2>
 * <pre>
 * WELCOME &lt;text&gt;
 * SEAT &lt;seat&gt;                      your seat number (0-3)
 * TOKEN &lt;key&gt;                      your secret seat key; keep it to REJOIN
 * RESUME ... RESUMED               a full snapshot of the table after REJOIN
 * TABLE &lt;seat&gt; &lt;card&gt; [card...]    (in a snapshot) the hand currently on the table
 * AWAY &lt;seat&gt;                      that player lost connection; their seat is held for a while
 * BACK &lt;seat&gt;                      that player reconnected
 * LEFT &lt;seat&gt;                      that player gave up their seat (a bot plays it now)
 * LOBBY &lt;room&gt; &lt;name,name,...&gt;    who is seated ("-" = empty)
 * OWNER &lt;seat&gt;                     who controls the settings
 * SETTINGS &lt;key=value ...&gt;         current settings
 * PHASE &lt;lobby|playing|between&gt;
 * DEAL &lt;n&gt;                         deal number, from 1
 * HAND [card...]                   your cards (only ever sent to you)
 * COUNTS &lt;n0&gt; &lt;n1&gt; &lt;n2&gt; &lt;n3&gt;      cards left per seat
 * TURN &lt;seat&gt; &lt;LEAD|FOLLOW&gt; &lt;s&gt;   whose turn; s = seconds on their timer, 0 = none
 * PLAYED &lt;seat&gt; &lt;card&gt; [card...]
 * PASSED &lt;seat&gt;
 * REACT &lt;seat&gt; &lt;id&gt;            someone sent a reaction
 * TIMEOUT &lt;seat&gt;                   their timer ran out; the move that follows was automatic
 * WIN &lt;seat&gt; &lt;name&gt;                won the deal
 * SCORES &lt;p0..p3&gt; &lt;t0..t3&gt;        this deal's penalties, then match totals
 * NEXT &lt;seconds&gt;                   the next deal starts in this many seconds
 * MATCH &lt;seat&gt; &lt;name&gt;              match over; lowest total wins
 * ERROR &lt;text&gt;
 * </pre>
 *
 * Try it without a client: {@code nc localhost 5050} and type the commands.
 */
public final class Protocol {

    public static final int DEFAULT_TCP_PORT = 5050;
    public static final int DEFAULT_HTTP_PORT = 8090;

    // Client -> server
    public static final String CREATE = "CREATE";
    public static final String JOIN = "JOIN";
    public static final String REJOIN = "REJOIN";
    public static final String SET = "SET";
    public static final String START = "START";
    public static final String PLAY = "PLAY";
    public static final String PASS = "PASS";
    public static final String REACT = "REACT";
    public static final String PING = "PING";
    public static final String QUIT = "QUIT";

    // Server -> client
    public static final String WELCOME = "WELCOME";
    public static final String SEAT = "SEAT";
    public static final String TOKEN = "TOKEN";
    public static final String RESUME = "RESUME";
    public static final String RESUMED = "RESUMED";
    public static final String TABLE = "TABLE";
    public static final String AWAY = "AWAY";
    public static final String BACK = "BACK";
    public static final String LEFT = "LEFT";
    public static final String LOBBY = "LOBBY";
    public static final String OWNER = "OWNER";
    public static final String SETTINGS = "SETTINGS";
    public static final String PHASE = "PHASE";
    public static final String DEAL = "DEAL";
    public static final String HAND = "HAND";
    public static final String COUNTS = "COUNTS";
    public static final String TURN = "TURN";
    public static final String PLAYED = "PLAYED";
    public static final String PASSED = "PASSED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String WIN = "WIN";
    public static final String SCORES = "SCORES";
    public static final String NEXT = "NEXT";
    public static final String MATCH = "MATCH";
    public static final String ERROR = "ERROR";

    private Protocol() {
    }
}
