package com.big2.server.protocol;

/**
 * The wire protocol: one UTF-8 text line per message, words separated by spaces.
 * Cards use short codes such as {@code 3D}, {@code 10S}, {@code QH}.
 *
 * <h2>Client to server</h2>
 * <pre>
 * JOIN &lt;room&gt; &lt;name&gt;     join (or create) a room
 * START                   start the game; empty seats are filled with bots
 * PLAY &lt;card&gt; [card...]   play cards, e.g. PLAY 3D 3S
 * PASS                    pass
 * QUIT                    leave
 * </pre>
 *
 * <h2>Server to client</h2>
 * <pre>
 * WELCOME &lt;text&gt;
 * LOBBY &lt;room&gt; &lt;name,name,...&gt;    who is seated
 * SEAT &lt;seat&gt;                      your seat number (0-3)
 * HAND [card...]                   your cards (only ever sent to you)
 * COUNTS &lt;n0&gt; &lt;n1&gt; &lt;n2&gt; &lt;n3&gt;      cards left per seat
 * TURN &lt;seat&gt; &lt;LEAD|FOLLOW&gt;
 * PLAYED &lt;seat&gt; &lt;card&gt; [card...]
 * PASSED &lt;seat&gt;
 * WIN &lt;seat&gt; &lt;name&gt;
 * ERROR &lt;text&gt;
 * </pre>
 *
 * Try it without a client: {@code nc localhost 5050} (or telnet) and type the commands.
 *
 * TODO (week 6): consider switching to JSON over WebSockets so a browser client can connect.
 */
public final class Protocol {

    public static final int DEFAULT_PORT = 5050;

    // Client -> server
    public static final String JOIN = "JOIN";
    public static final String START = "START";
    public static final String PLAY = "PLAY";
    public static final String PASS = "PASS";
    public static final String QUIT = "QUIT";

    // Server -> client
    public static final String WELCOME = "WELCOME";
    public static final String LOBBY = "LOBBY";
    public static final String SEAT = "SEAT";
    public static final String HAND = "HAND";
    public static final String COUNTS = "COUNTS";
    public static final String TURN = "TURN";
    public static final String PLAYED = "PLAYED";
    public static final String PASSED = "PASSED";
    public static final String WIN = "WIN";
    public static final String ERROR = "ERROR";

    private Protocol() {
    }
}
