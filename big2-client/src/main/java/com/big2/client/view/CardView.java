package com.big2.client.view;

import com.big2.core.card.Card;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * One classic playing card, drawn with Java2D (no image files needed).
 * Hovered cards rise slightly; selected cards rise further and get a gold edge.
 */
public final class CardView extends JComponent {

    static final int CARD_W = 60;
    static final int CARD_H = 88;
    static final int LIFT = 22;
    /** Full component height: the card plus the room it needs to rise. */
    static final int COMP_H = CARD_H + LIFT + 8;
    static final int COMP_W = CARD_W + 8;

    private final Card card;
    private final boolean selectable;
    private boolean selected;
    private boolean hover;

    public CardView(Card card, boolean selectable, Consumer<CardView> onToggle) {
        this.card = card;
        this.selectable = selectable;
        setPreferredSize(new Dimension(COMP_W, COMP_H));
        setOpaque(false);
        if (selectable) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    selected = !selected;
                    repaint();
                    onToggle.accept(CardView.this);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }
    }

    public Card card() {
        return card;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        Theme.smooth(g);

        int x = 4;
        int y = selected ? 4 : (selectable && hover) ? LIFT - 8 : LIFT;

        // Soft shadow
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRoundRect(x + 1, y + 3, CARD_W, CARD_H, 10, 10);

        // Face
        g.setColor(Theme.IVORY);
        g.fillRoundRect(x, y, CARD_W, CARD_H, 10, 10);
        g.setStroke(new BasicStroke(selected ? 3f : 1.2f));
        g.setColor(selected ? Theme.GOLD : new Color(0x3A3D45));
        g.drawRoundRect(x, y, CARD_W, CARD_H, 10, 10);

        Color ink = card.suit().isRed() ? Theme.RED : Theme.INK;

        // Corner index: rank over a small suit
        g.setFont(Theme.serif(Font.BOLD, 20f));
        FontMetrics fm = g.getFontMetrics();
        String rank = card.rank().label();
        g.setColor(ink);
        g.drawString(rank, x + 6, y + 21);
        Theme.drawSuit(g, card.suit(), x + 6 + fm.stringWidth(rank) / 2.0, y + 35, 13);

        // Large centre suit
        Theme.drawSuit(g, card.suit(), x + CARD_W / 2.0 + 5, y + CARD_H * 0.64, 36);
        g.dispose();
    }
}
