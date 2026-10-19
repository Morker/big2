package com.big2.server;

import com.big2.server.protocol.Protocol;
import com.big2.server.room.RoomManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point. Accepts TCP connections and gives each client its own virtual thread.
 *
 * <p>Run: {@code mvn -pl big2-server exec:java} (optionally {@code -Dexec.args=6000} for another port).
 */
public final class Big2Server {

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.DEFAULT_PORT;
        RoomManager rooms = new RoomManager();

        try (ServerSocket server = new ServerSocket(port);
             ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("Big 2 server listening on port " + port);
            while (true) {
                Socket socket = server.accept();
                clients.submit(new ClientConnection(socket, rooms));
            }
        }
    }
}
