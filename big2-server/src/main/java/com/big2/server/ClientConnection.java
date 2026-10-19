package com.big2.server;

import com.big2.server.room.RoomManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** A plain TCP player, one line per command. Mainly for testing the protocol with nc or telnet. */
public final class ClientConnection implements Runnable {

    private final Socket socket;
    private final RoomManager rooms;

    public ClientConnection(Socket socket, RoomManager rooms) {
        this.socket = socket;
        this.rooms = rooms;
    }

    @Override
    public void run() {
        PlayerSession session = null;
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            session = new PlayerSession(rooms, out::println);
            session.welcome();

            String line;
            while ((line = in.readLine()) != null) {
                if (!session.handle(line)) {
                    break;
                }
            }
        } catch (IOException e) {
            // Client disconnected abruptly; fall through to cleanup.
        } finally {
            if (session != null) {
                session.disconnected();
            }
        }
    }
}
