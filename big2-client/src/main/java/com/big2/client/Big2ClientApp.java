package com.big2.client;

import com.big2.client.controller.LocalGameController;
import com.big2.client.controller.RemoteGameController;
import com.big2.client.view.GameWindow;
import com.big2.client.view.MenuWindow;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;

/**
 * Starts the desktop client: a menu to play against bots or online with friends.
 *
 * <p>Run: {@code mvn -pl big2-client exec:java}
 */
public final class Big2ClientApp {

    private Big2ClientApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
                // Default look and feel is fine.
            }
            MenuWindow[] menu = new MenuWindow[1];
            menu[0] = new MenuWindow(
                    () -> playLocal(menu[0]),
                    (host, port, room, name) -> playOnline(menu[0], host, port, room, name));
            menu[0].setVisible(true);
        });
    }

    private static void playLocal(MenuWindow menu) {
        LocalGameController controller = new LocalGameController();
        GameWindow window = new GameWindow(controller);
        menu.dispose();
        window.setVisible(true);
        controller.newGame();
    }

    private static void playOnline(MenuWindow menu, String host, int port, String room, String name) {
        RemoteGameController controller = new RemoteGameController(host, port, room, name);
        GameWindow window = new GameWindow(controller); // registers the listener before any message arrives
        try {
            controller.connect();
        } catch (IOException e) {
            window.dispose();
            JOptionPane.showMessageDialog(menu,
                    "Couldn't connect to " + host + ":" + port + "\n" + e.getMessage()
                            + "\n\nIs the server running, and is the address right?",
                    "Connection failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        menu.dispose();
        window.setVisible(true);
    }
}
