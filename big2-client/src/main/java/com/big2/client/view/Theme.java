package com.big2.client.view;

import com.big2.core.card.Suit;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Colours, fonts and drawing helpers shared by all views: a dark, casino-style look with gold accents. */
final class Theme {

    static final Color INK = new Color(0x14161B);
    static final Color RED = new Color(0xB3202A);
    static final Color BG_TOP = new Color(0x1A1F27);
    static final Color BG_BOTTOM = new Color(0x0B0E13);
    static final Color FELT = new Color(0x14633F);
    static final Color FELT_DARK = new Color(0x0C3F29);
    static final Color WOOD = new Color(0x3A2A1D);
    static final Color GOLD = new Color(0xC9A24B);
    static final Color PANEL = new Color(0x171C23);
    static final Color SLATE = new Color(0x2A323D);
    static final Color LINE = new Color(0x46505E);
    static final Color TEXT = new Color(0xE8E4D8);
    static final Color TEXT_DIM = new Color(0x9AA3AF);
    static final Color IVORY = new Color(0xFBF9F3);

    private static final Set<String> FAMILIES = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    private static final String SANS = pick(Font.SANS_SERIF, "Segoe UI", "Helvetica Neue", "Arial");
    private static final String SERIF = pick(Font.SERIF, "Georgia", "Palatino Linotype", "Times New Roman");

    private Theme() {
    }

    /** UI font (buttons, labels). */
    static Font font(int style, float size) {
        return new Font(SANS, style, 12).deriveFont(size);
    }

    /** Display font (ranks, title, numbers). */
    static Font serif(int style, float size) {
        return new Font(SERIF, style, 12).deriveFont(size);
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Fills a shape, then draws a line of the given colour and width around it. */
    static void fillStroked(Graphics2D g, Shape shape, Color fill, Color line, float strokeWidth) {
        g.setColor(fill);
        g.fill(shape);
        g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(line);
        g.draw(shape);
    }

    /** Draws text with letter spacing (used for small caps headings). Returns the width drawn. */
    static float spacedText(Graphics2D g, String text, float x, float y, float spacing) {
        float cursor = x;
        for (char c : text.toCharArray()) {
            String s = String.valueOf(c);
            g.drawString(s, cursor, y);
            cursor += g.getFontMetrics().stringWidth(s) + spacing;
        }
        return cursor - x - spacing;
    }

    static float spacedWidth(Graphics2D g, String text, float spacing) {
        float w = 0;
        for (char c : text.toCharArray()) {
            w += g.getFontMetrics().stringWidth(String.valueOf(c)) + spacing;
        }
        return w - spacing;
    }

    /** Draws a suit as a vector shape centred on (cx, cy); {@code s} is roughly its height. */
    static void drawSuit(Graphics2D g, Suit suit, double cx, double cy, double s) {
        drawSuit(g, suit, cx, cy, s, suit.isRed() ? RED : INK);
    }

    static void drawSuit(Graphics2D g, Suit suit, double cx, double cy, double s, Color color) {
        Area area = new Area();
        switch (suit) {
            case DIAMONDS -> {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, cy - 0.55 * s);
                p.lineTo(cx + 0.42 * s, cy);
                p.lineTo(cx, cy + 0.55 * s);
                p.lineTo(cx - 0.42 * s, cy);
                p.closePath();
                area.add(new Area(p));
            }
            case HEARTS -> {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, cy + 0.5 * s);
                p.curveTo(cx - 0.95 * s, cy - 0.05 * s, cx - 0.55 * s, cy - 0.62 * s, cx, cy - 0.2 * s);
                p.curveTo(cx + 0.55 * s, cy - 0.62 * s, cx + 0.95 * s, cy - 0.05 * s, cx, cy + 0.5 * s);
                p.closePath();
                area.add(new Area(p));
            }
            case SPADES -> {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, cy - 0.5 * s);
                p.curveTo(cx - 0.95 * s, cy + 0.05 * s, cx - 0.55 * s, cy + 0.62 * s, cx, cy + 0.2 * s);
                p.curveTo(cx + 0.55 * s, cy + 0.62 * s, cx + 0.95 * s, cy + 0.05 * s, cx, cy - 0.5 * s);
                p.closePath();
                area.add(new Area(p));
                area.add(stem(cx, cy + 0.1 * s, cy + 0.6 * s, 0.22 * s));
            }
            case CLUBS -> {
                double r = 0.27 * s;
                area.add(new Area(new Ellipse2D.Double(cx - r, cy - 0.3 * s - r, 2 * r, 2 * r)));
                area.add(new Area(new Ellipse2D.Double(cx - 0.3 * s - r, cy + 0.08 * s - r, 2 * r, 2 * r)));
                area.add(new Area(new Ellipse2D.Double(cx + 0.3 * s - r, cy + 0.08 * s - r, 2 * r, 2 * r)));
                area.add(stem(cx, cy, cy + 0.6 * s, 0.22 * s));
            }
        }
        g.setColor(color);
        g.fill(area);
    }

    private static Area stem(double cx, double top, double bottom, double halfWidth) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, top);
        p.lineTo(cx - halfWidth, bottom);
        p.lineTo(cx + halfWidth, bottom);
        p.closePath();
        return new Area(p);
    }

    private static String pick(String fallback, String... wanted) {
        for (String family : wanted) {
            if (FAMILIES.contains(family)) {
                return family;
            }
        }
        return fallback;
    }
}
