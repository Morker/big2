package com.big2.server;

import com.big2.server.protocol.Protocol;
import com.big2.server.room.RoomManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point. Starts the web server (browser client + WebSocket) and a plain TCP
 * listener for testing with nc. Both share the same rooms.
 *
 * <p>Ports: HTTP from {@code --http=N}, else the {@code PORT} environment variable (what most
 * cloud hosts set), else 8090. TCP from {@code --tcp=N}, default 5050; {@code --tcp=0} disables it.
 * {@code --host=127.0.0.1} listens only on the machine itself, for running behind a proxy such as Caddy.
 * {@code --static=DIR} serves the web client from a folder instead of the jar (for development).
 *
 * <p>Run: {@code mvn -pl big2-server exec:java}, or {@code java -jar big2-server/target/big2-server.jar}.
 */
public final class Big2Server {

    public static void main(String[] args) throws IOException {
        int httpPort = intOrDefault(System.getenv("PORT"), Protocol.DEFAULT_HTTP_PORT);
        int tcpPort = Protocol.DEFAULT_TCP_PORT;
        String host = "0.0.0.0";
        String staticDir = null;
        for (String arg : args) {
            if (arg.startsWith("--http=")) {
                httpPort = Integer.parseInt(arg.substring(7));
            } else if (arg.startsWith("--host=")) {
                host = arg.substring(7);
            } else if (arg.startsWith("--tcp=")) {
                tcpPort = Integer.parseInt(arg.substring(6));
            } else if (arg.startsWith("--static=")) {
                staticDir = arg.substring(9);
            } else {
                System.err.println("Unknown argument: " + arg + " (use --http=PORT, --host=ADDRESS, --tcp=PORT, --static=DIR)");
            }
        }
        RoomManager rooms = new RoomManager();

        WebServer.start(host, httpPort, rooms, staticDir);
        System.out.println("Big 2 is running. Open http://localhost:" + httpPort + " in your browser.");

        if (tcpPort > 0) {
            try (ServerSocket server = new ServerSocket(tcpPort);
                 ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor()) {
                System.out.println("TCP protocol listener on port " + tcpPort + " (try: nc localhost " + tcpPort + ")");
                while (true) {
                    Socket socket = server.accept();
                    clients.submit(new ClientConnection(socket, rooms));
                }
            }
        }
    }

    private static int intOrDefault(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
