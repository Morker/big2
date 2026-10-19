package com.big2.server;

import com.big2.server.room.RoomManager;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

/**
 * Serves the web client (from {@code src/main/resources/public}) and the game itself
 * over a WebSocket at {@code /ws}. Each WebSocket message is one protocol line.
 */
public final class WebServer {

    private static final String SESSION = "session";

    private WebServer() {
    }

    /**
     * @param staticDir a folder to serve the web client from instead of the jar, so edits show
     *                  on reload during development; null to use the files packaged in the jar
     */
    public static Javalin start(int port, RoomManager rooms, String staticDir) {
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            if (staticDir == null) {
                config.staticFiles.add("/public", Location.CLASSPATH);
            } else {
                config.staticFiles.add(staticDir, Location.EXTERNAL);
            }
        });

        app.get("/health", ctx -> ctx.result("ok"));

        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                ctx.enableAutomaticPings();
                PlayerSession session = new PlayerSession(rooms, ctx::send);
                ctx.attribute(SESSION, session);
                session.welcome();
            });
            ws.onMessage(ctx -> {
                PlayerSession session = ctx.attribute(SESSION);
                if (session != null && !session.handle(ctx.message())) {
                    ctx.closeSession();
                }
            });
            ws.onClose(ctx -> {
                PlayerSession session = ctx.attribute(SESSION);
                if (session != null) {
                    session.disconnected();
                }
            });
            ws.onError(ctx -> {
                PlayerSession session = ctx.attribute(SESSION);
                if (session != null) {
                    session.disconnected();
                }
            });
        });

        return app.start(port);
    }
}
