package com.big2.client.view;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** A flat rounded button: gold for the main action, slate for the rest. */
final class ActionButton extends JButton {

    private final boolean primary;

    ActionButton(String text, boolean primary) {
        super(text);
        this.primary = primary;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setFont(Theme.font(Font.BOLD, 15f));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(Math.max(104, fm.stringWidth(getText()) + 44), 44);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        Theme.smooth(g);
        int w = getWidth() - 1;
        int h = getHeight() - 1;
        boolean hover = isEnabled() && getModel().isRollover();
        boolean pressed = isEnabled() && getModel().isArmed() && getModel().isPressed();

        Color fill;
        Color line;
        Color text;
        if (!isEnabled()) {
            fill = new Color(0x1C2128);
            line = new Color(0x2A323D);
            text = new Color(0x5B6470);
        } else if (primary) {
            fill = pressed ? Theme.GOLD.darker() : hover ? Theme.GOLD.brighter() : Theme.GOLD;
            line = Theme.GOLD.darker();
            text = Theme.INK;
        } else {
            fill = pressed ? Theme.SLATE.darker() : hover ? Theme.SLATE.brighter() : Theme.SLATE;
            line = hover ? Theme.GOLD : Theme.LINE;
            text = Theme.TEXT;
        }
        g.setColor(fill);
        g.fillRoundRect(0, 0, w, h, 12, 12);
        g.setColor(line);
        g.drawRoundRect(0, 0, w, h, 12, 12);

        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        g.setColor(text);
        g.drawString(getText(), (w - fm.stringWidth(getText())) / 2,
                (h - fm.getHeight()) / 2 + fm.getAscent() + (pressed ? 1 : 0));
        g.dispose();
    }
}
