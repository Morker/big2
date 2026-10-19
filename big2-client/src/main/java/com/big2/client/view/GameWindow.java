package com.big2.client.view;

import com.big2.client.controller.GameController;
import com.big2.client.controller.TableState;
import com.big2.core.card.Card;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The main window: opponents along the top, the table in the middle,
 * your hand and action buttons along the bottom.
 *
 * <p>The window never touches game rules. It shows a {@link TableState} and
 * forwards clicks to the {@link GameController}.
 */
public final class GameWindow extends JFrame {

    private static final Color FELT = new Color(0x1B, 0x5E, 0x20);
    private static final Color TEXT = new Color(0xE8, 0xF5, 0xE9);
    private static final Color TURN = new Color(0xFF, 0xD5, 0x4F);

    private final GameController controller;
    private final Set<Card> selected = new HashSet<>();

    private final JPanel opponents = transparent(new JPanel(new GridLayout(1, 3)));
    private final JPanel tableCards = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0)));
    private final JLabel tableLabel = label("", 15f);
    private final JLabel status = label("", 16f);
    private final JLabel youLabel = label("", 15f);
    private final JPanel handPanel = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0)));
    private final JButton playButton = new JButton("Play");
    private final JButton passButton = new JButton("Pass");
    private final JButton clearButton = new JButton("Clear");
    private final JButton newGameButton = new JButton("New game");

    public GameWindow(GameController controller) {
        super("Big 2");
        this.controller = controller;

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(FELT);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setContentPane(root);

        root.add(opponents, BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildBottom(), BorderLayout.SOUTH);

        playButton.addActionListener(e -> {
            List<Card> cards = new ArrayList<>(selected);
            selected.clear();
            controller.play(cards);
        });
        passButton.addActionListener(e -> controller.pass());
        clearButton.addActionListener(e -> {
            selected.clear();
            for (Component c : handPanel.getComponents()) {
                ((CardView) c).setSelected(false);
            }
        });
        newGameButton.addActionListener(e -> {
            selected.clear();
            controller.newGame();
        });

        controller.setListener(this::render);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 560));
        setLocationRelativeTo(null);
    }

    private JPanel buildCenter() {
        JPanel center = transparent(new JPanel());
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(Box.createVerticalGlue());
        center.add(centered(tableLabel));
        center.add(Box.createVerticalStrut(8));
        center.add(tableCards);
        center.add(Box.createVerticalStrut(12));
        center.add(centered(status));
        center.add(Box.createVerticalGlue());
        return center;
    }

    private JPanel buildBottom() {
        JPanel buttons = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0)));
        buttons.add(playButton);
        buttons.add(passButton);
        buttons.add(clearButton);
        buttons.add(Box.createHorizontalStrut(30));
        buttons.add(newGameButton);

        JPanel bottom = transparent(new JPanel());
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(centered(youLabel));
        bottom.add(handPanel);
        bottom.add(Box.createVerticalStrut(8));
        bottom.add(buttons);
        return bottom;
    }

    /** Redraws everything from a new state. Always runs on the Swing thread. */
    private void render(TableState s) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> render(s));
            return;
        }

        opponents.removeAll();
        for (int i = 1; i < s.names().size(); i++) {
            int seat = (s.mySeat() + i) % s.names().size();
            JLabel l = label(s.names().get(seat) + "  •  " + s.cardCounts().get(seat) + " cards", 16f);
            l.setHorizontalAlignment(SwingConstants.CENTER);
            if (seat == s.currentSeat() && !s.over()) {
                l.setForeground(TURN);
                l.setText("▶ " + l.getText());
            }
            opponents.add(l);
        }

        tableCards.removeAll();
        if (s.tableCards().isEmpty()) {
            String leader = s.myTurn() ? "your lead" : s.names().get(s.currentSeat()) + " leads";
            tableLabel.setText(s.over() ? "Game over" : "Table is empty — " + leader);
        } else {
            tableLabel.setText("On the table (" + s.names().get(s.tableOwner()) + "):");
            for (Card c : s.tableCards()) {
                tableCards.add(new CardView(c, false, v -> { }));
            }
        }
        status.setText(s.message());

        selected.retainAll(s.myHand());
        handPanel.removeAll();
        for (Card c : s.myHand()) {
            CardView view = new CardView(c, true, v -> {
                if (v.isSelected()) {
                    selected.add(v.card());
                } else {
                    selected.remove(v.card());
                }
            });
            view.setSelected(selected.contains(c));
            handPanel.add(view);
        }

        youLabel.setText(s.myTurn() ? "▶ Your turn" : "Your hand");
        youLabel.setForeground(s.myTurn() ? TURN : TEXT);
        playButton.setEnabled(s.myTurn());
        passButton.setEnabled(s.myTurn() && !s.leading());

        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private static JLabel label(String text, float size) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        l.setFont(l.getFont().deriveFont(Font.BOLD, size));
        return l;
    }

    private static JPanel centered(JLabel label) {
        JPanel p = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)));
        p.add(label);
        return p;
    }

    private static JPanel transparent(JPanel panel) {
        panel.setOpaque(false);
        return panel;
    }
}
