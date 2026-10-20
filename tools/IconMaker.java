import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the app icons (a gold-edged 2♠ card on dark felt) into the web client's icons folder.
 *
 * <p>Run from the project root: {@code java tools/IconMaker.java}
 */
public final class IconMaker {

    private static final Color BG_CENTER = new Color(0x1B3A2D);
    private static final Color BG_EDGE = new Color(0x0B100E);
    private static final Color CARD = new Color(0xFBF8F1);
    private static final Color GOLD = new Color(0xC9A24B);
    private static final Color INK = new Color(0x16181D);
    private static final String OUT = "big2-server/src/main/resources/public/icons/";

    public static void main(String[] args) throws IOException {
        new File(OUT).mkdirs();
        write(512, 0.78, "icon-512.png");
        write(192, 0.78, "icon-192.png");
        write(180, 0.74, "apple-touch-icon.png");
        write(32, 0.92, "favicon-32.png");
        // Maskable: Android may crop to a circle, so keep the card inside the central safe zone.
        write(512, 0.58, "icon-maskable-512.png");
        System.out.println("Icons written to " + OUT);
    }

    /** {@code cardScale} is the card's height as a fraction of the icon. */
    private static void write(int size, double cardScale, String name) throws IOException {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        float c = size / 2f;
        g.setPaint(new RadialGradientPaint(c, c * 0.9f, size * 0.75f, new float[]{0f, 1f}, new Color[]{BG_CENTER, BG_EDGE}));
        g.fillRect(0, 0, size, size);

        double ch = size * cardScale, cw = ch / 1.4;
        g.rotate(Math.toRadians(-8), c, c);
        double x = c - cw / 2, y = c - ch / 2, r = cw * 0.12;

        if (size >= 64) {
            g.setColor(new Color(0, 0, 0, 90));
            g.fill(new RoundRectangle2D.Double(x + cw * 0.03, y + ch * 0.04, cw, ch, r, r));
        }
        g.setColor(CARD);
        g.fill(new RoundRectangle2D.Double(x, y, cw, ch, r, r));
        g.setStroke(new BasicStroke((float) Math.max(1.2, cw * 0.035)));
        g.setColor(GOLD);
        g.draw(new RoundRectangle2D.Double(x, y, cw, ch, r, r));

        // Large spade in the middle.
        g.setColor(INK);
        g.fill(spade(c, c + ch * 0.04, ch * 0.42));

        // Corner index "2" (skipped on the favicon, where it would be a smudge).
        if (size >= 64) {
            Font f = new Font("Georgia", Font.BOLD, (int) Math.round(ch * 0.2));
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            g.setColor(INK);
            float tx = (float) (x + cw * 0.1);
            float ty = (float) (y + cw * 0.08 + fm.getAscent() * 0.8);
            g.drawString("2", tx, ty);
            g.rotate(Math.toRadians(180), c, c);
            g.drawString("2", tx, ty);
        }
        g.dispose();
        ImageIO.write(img, "png", new File(OUT + name));
    }

    /** The same spade shape the cards use, centred on (cx, cy), about {@code s} tall. */
    private static Area spade(double cx, double cy, double s) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - 0.5 * s);
        p.curveTo(cx - 0.95 * s, cy + 0.05 * s, cx - 0.55 * s, cy + 0.62 * s, cx, cy + 0.2 * s);
        p.curveTo(cx + 0.55 * s, cy + 0.62 * s, cx + 0.95 * s, cy + 0.05 * s, cx, cy - 0.5 * s);
        p.closePath();
        Area a = new Area(p);
        Path2D stem = new Path2D.Double();
        stem.moveTo(cx, cy + 0.1 * s);
        stem.lineTo(cx - 0.22 * s, cy + 0.6 * s);
        stem.lineTo(cx + 0.22 * s, cy + 0.6 * s);
        stem.closePath();
        a.add(new Area(stem));
        return a;
    }
}
