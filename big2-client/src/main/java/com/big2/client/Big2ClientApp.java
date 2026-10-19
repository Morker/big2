package com.big2.client;

import com.big2.client.controller.GameController;
import com.big2.client.controller.LocalGameController;
import com.big2.client.view.GameWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Starts the desktop client in single-player mode against three bots.
 *
 * <p>Run: {@code mvn -pl big2-client exec:java}
 *
 * TODO (week 6): show a start screen with "Play vs bots" and "Play online",
 * and use {@link com.big2.client.controller.RemoteGameController} for online games.
 */
public final class Big2ClientApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
                // Default look and feel is fine.
            }
            GameController controller = new LocalGameController();
            GameWindow window = new GameWindow(controller);
            window.setVisible(true);
            controller.newGame();
        });
    }
}
