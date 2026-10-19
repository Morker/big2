package com.big2.client.view;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A player's seat marker: a monogram medallion, name, and cards left.
 * The medallion glows gold on that player's turn, and the winner is labelled.
 */
final class PlayerBadge extends JComponent {

    private String name = "";
    private int cards = -1;
    private boolean active;
    private boolean winner;

    PlayerBadge() {
        setPreferredSize(new Dimension(170, 150));
        setOpaque(false);
    }

    /** {@code cards < 0} hides the card counter (used in the waiting room). */
    void set(int seat, String name, int cards, boolean active, boolean winner) {
        this.name = name;
        this.cards = cards;
        this.active = active;
        this.winner = winner;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        Theme.smooth(g);

        double cx = getWidth() / 2.0;
        double cy = 52;
        boolean empty = name.isEmpty() || name.equals("Empty seat");

        if (active) {
            for (int i = 3; i >= 1; i--) {
                g.setColor(new Color(201, 162, 75, 28 * (4 - i)));
                double r = 30 + i * 6;
                g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            }
        }

        Ellipse2D medallion = new Ellipse2D.Double(cx - 30, cy - 30, 60, 60);
        Theme.fillStroked(g, medallion, empty ? new Color(0x1C2128) : Theme.SLATE,
                active || winner ? Theme.GOLD : Theme.LINE, active || winner ? 3f : 1.5f);

        g.setFont(Theme.serif(Font.BOLD, 28f));
        FontMetrics fm = g.getFontMetrics();
        String initial = empty ? "?" : name.substring(0, 1).toUpperCase();
        g.setColor(empty ? Theme.LINE : Theme.TEXT);
        g.drawString(initial, (float) (cx - fm.stringWidth(initial) / 2.0), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));

        if (cards >= 0) {
            drawCardCount(g, cx + 26, cy - 34);
        }

        g.setFont(Theme.font(Font.BOLD, 15f));
        fm = g.getFontMetrics();
        String text = name.length() > 14 ? name.substring(0, 13) + "…" : name;
        g.setColor(empty ? Theme.TEXT_DIM : winner || active ? Theme.GOLD : Theme.TEXT);
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), 104f);

        if (winner) {
            g.setFont(Theme.font(Font.BOLD, 11f));
            fm = g.getFontMetrics();
            g.setColor(Theme.GOLD);
            Theme.spacedText(g, "WINNER", (float) (cx - Theme.spacedWidth(g, "WINNER", 2f) / 2.0), 122f, 2f);
        }
        g.dispose();
    }

    /** A small card-back chip with the number of cards left. */
    private void drawCardCount(Graphics2D g, double x, double y) {
        RoundRectangle2D chip = new RoundRectangle2D.Double(x, y, 38, 26, 13, 13);
        boolean low = cards > 0 && cards <= 3;
        Theme.fillStroked(g, chip, Theme.PANEL, low ? Theme.RED.brighter() : Theme.GOLD, 1.5f);
        g.setFont(Theme.serif(Font.BOLD, 15f));
        FontMetrics fm = g.getFontMetrics();
        String text = String.valueOf(cards);
        g.setColor(low ? new Color(0xFF6B6B) : Theme.TEXT);
        g.drawString(text, (float) (x + (38 - fm.stringWidth(text)) / 2.0), (float) (y + 18));
    }
}
