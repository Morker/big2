package com.big2.client.view;

import com.big2.client.controller.GameController;
import com.big2.client.controller.TableState;
import com.big2.core.card.Card;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.BasicStroke;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The main window: opponents around a cartoon table, your hand and buttons at the bottom.
 *
 * <p>The window never touches game rules. It shows a {@link TableState} and
 * forwards clicks to the {@link GameController}.
 */
public final class GameWindow extends JFrame {

    private final Set<Card> selected = new HashSet<>();

    /** By position relative to you: 0 = you (bottom), 1 = left, 2 = top, 3 = right. */
    private final PlayerBadge[] badges = {new PlayerBadge(), new PlayerBadge(), new PlayerBadge(), new PlayerBadge()};
    private final JLabel tableLabel = new JLabel("", SwingConstants.CENTER);
    private final JPanel tableCards = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, -4, 0)));
    private final Bubble status = new Bubble();
    private final JPanel handPanel = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, -4, 0)));
    private final ActionButton playButton = new ActionButton("Play", true);
    private final ActionButton passButton = new ActionButton("Pass", false);
    private final ActionButton clearButton = new ActionButton("Clear", false);
    private final ActionButton newGameButton;

    public GameWindow(GameController controller) {
        super("Big 2");
        newGameButton = new ActionButton(controller.newGameLabel(), false);

        BackgroundPanel root = new BackgroundPanel(new BorderLayout(8, 4));
        root.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));
        setContentPane(root);

        root.add(wrap(badges[2]), BorderLayout.NORTH);
        root.add(wrapMiddle(badges[1]), BorderLayout.WEST);
        root.add(wrapMiddle(badges[3]), BorderLayout.EAST);
        root.add(new TableOval(), BorderLayout.CENTER);
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
        setMinimumSize(new Dimension(1040, 740));
        setSize(1120, 780);
        setLocationRelativeTo(null);
    }

    private JPanel buildBottom() {
        handPanel.setPreferredSize(new Dimension(1, CardView.COMP_H));

        JPanel buttons = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0)));
        buttons.add(playButton);
        buttons.add(passButton);
        buttons.add(clearButton);
        buttons.add(Box.createHorizontalStrut(30));
        buttons.add(newGameButton);

        JPanel center = transparent(new JPanel(new BorderLayout(0, 8)));
        center.add(handPanel, BorderLayout.CENTER);
        center.add(buttons, BorderLayout.SOUTH);

        JPanel bottom = transparent(new JPanel(new BorderLayout(6, 0)));
        bottom.add(badges[0], BorderLayout.WEST);
        bottom.add(center, BorderLayout.CENTER);
        // Keeps the hand centred on the window rather than shifted by your avatar.
        bottom.add(Box.createHorizontalStrut(170), BorderLayout.EAST);
        return bottom;
    }

    /** Redraws everything from a new state. Always runs on the Swing thread. */
    private void render(TableState s) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> render(s));
            return;
        }

        int seats = s.names().size();
        boolean waiting = s.currentSeat() < 0;
        for (int rel = 0; rel < seats; rel++) {
            int seat = (s.mySeat() + rel) % seats;
            boolean active = !s.over() && seat == s.currentSeat();
            badges[rel].set(seat, rel == 0 ? "You" : s.names().get(seat),
                    waiting ? -1 : s.cardCounts().get(seat), active, s.winnerSeat() == seat);
        }

        tableCards.removeAll();
        if (waiting) {
            tableLabel.setText("Waiting room");
        } else if (s.over()) {
            tableLabel.setText("Game over!");
        } else if (s.tableCards().isEmpty()) {
            tableLabel.setText(s.myTurn() ? "Table is empty: you lead!" : s.names().get(s.currentSeat()) + " leads");
        } else {
            tableLabel.setText(s.names().get(s.tableOwner()) + " played:");
        }
        for (Card c : s.tableCards()) {
            tableCards.add(new CardView(c, false, v -> { }));
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

        playButton.setEnabled(s.myTurn());
        passButton.setEnabled(s.myTurn() && !s.leading());

        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private static JPanel wrap(Component c) {
        JPanel p = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)));
        p.add(c);
        return p;
    }

    private static JPanel wrapMiddle(Component c) {
        JPanel p = transparent(new JPanel(new GridBagLayout()));
        p.add(c);
        return p;
    }

    private static JPanel transparent(JPanel panel) {
        panel.setOpaque(false);
        return panel;
    }

    /** The green oval table with its orange rim. Holds the played cards and the status bubble. */
    private final class TableOval extends JPanel {

        TableOval() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));

            tableLabel.setFont(Theme.serif(Font.ITALIC, 19f));
            tableLabel.setForeground(Theme.TEXT);
            tableLabel.setAlignmentX(CENTER_ALIGNMENT);
            tableCards.setAlignmentX(CENTER_ALIGNMENT);
            tableCards.setPreferredSize(new Dimension(1, CardView.COMP_H));
            tableCards.setMaximumSize(new Dimension(Integer.MAX_VALUE, CardView.COMP_H));
            status.setAlignmentX(CENTER_ALIGNMENT);

            add(Box.createVerticalGlue());
            add(tableLabel);
            add(Box.createVerticalStrut(6));
            add(tableCards);
            add(Box.createVerticalStrut(10));
            add(status);
            add(Box.createVerticalGlue());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            int w = getWidth();
            int h = getHeight();
            // Shadow, wooden rail, then felt lit from the centre with a thin gold inlay.
            g.setColor(new Color(0, 0, 0, 110));
            g.fill(new Ellipse2D.Double(8, 16, w - 16, h - 20));
            Theme.fillStroked(g, new Ellipse2D.Double(6, 6, w - 12, h - 16), Theme.WOOD, new Color(0x1E150E), 2f);
            Ellipse2D felt = new Ellipse2D.Double(24, 24, w - 48, h - 52);
            float radius = (float) Math.max(felt.getWidth(), felt.getHeight()) / 2f;
            g.setPaint(new RadialGradientPaint(
                    (float) felt.getCenterX(), (float) felt.getCenterY(), radius,
                    new float[]{0f, 1f}, new Color[]{Theme.FELT, Theme.FELT_DARK}));
            g.fill(felt);
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(new Color(201, 162, 75, 150));
            g.draw(new Ellipse2D.Double(18, 18, w - 36, h - 40));
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    /** A dark pill for the status line. */
    private static final class Bubble extends JLabel {

        Bubble() {
            super("", SwingConstants.CENTER);
            setFont(Theme.font(Font.PLAIN, 15f));
            setForeground(Theme.TEXT);
            setBorder(BorderFactory.createEmptyBorder(7, 22, 7, 22));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            Theme.fillStroked(g, new RoundRectangle2D.Double(1, 1, getWidth() - 3, getHeight() - 3, 16, 16),
                    new Color(10, 13, 17, 210), new Color(201, 162, 75, 120), 1.2f);
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
