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
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Draws one playing card with Java2D, so no image files are needed.
 * Selected cards are raised and outlined.
 *
 * TODO (polish week): swap in real card artwork from a sprite sheet.
 */
public final class CardView extends JComponent {

    static final int WIDTH = 62;
    static final int HEIGHT = 88;
    private static final int LIFT = 16;
    private static final Color RED = new Color(0xC6, 0x28, 0x28);
    private static final Color BLACK = new Color(0x21, 0x21, 0x21);
    private static final Color HIGHLIGHT = new Color(0xFF, 0xC1, 0x07);

    private final Card card;
    private boolean selected;

    public CardView(Card card, boolean selectable, Consumer<CardView> onToggle) {
        this.card = card;
        setPreferredSize(new Dimension(WIDTH, HEIGHT + LIFT));
        if (selectable) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selected = !selected;
                    repaint();
                    onToggle.accept(CardView.this);
                }
            });
        }
        setToolTipText(card.pretty());
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
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int y = selected ? 0 : LIFT;
        g.setColor(Color.WHITE);
        g.fillRoundRect(0, y, WIDTH - 1, HEIGHT - 1, 10, 10);
        g.setStroke(new BasicStroke(selected ? 3f : 1f));
        g.setColor(selected ? HIGHLIGHT : Color.GRAY);
        g.drawRoundRect(0, y, WIDTH - 1, HEIGHT - 1, 10, 10);

        g.setColor(card.suit().isRed() ? RED : BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(card.rank().label(), 6, y + 18);
        g.drawString(card.suit().symbol(), 6, y + 34);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 34));
        FontMetrics fm = g.getFontMetrics();
        String symbol = card.suit().symbol();
        g.drawString(symbol, (WIDTH - fm.stringWidth(symbol)) / 2 + 6, y + HEIGHT - 16);
        g.dispose();
    }
}
