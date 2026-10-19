package com.big2.client.view;

import com.big2.core.card.Suit;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** The start screen: play against bots, or join friends online. */
public final class MenuWindow extends JFrame {

    /** Called when the player asks to join an online game. */
    public interface OnlineLauncher {
        void launch(String host, int port, String room, String name);
    }

    private final JTextField nameField = field(System.getProperty("user.name", "Player"));
    private final JTextField hostField = field("localhost");
    private final JTextField portField = field("5050");
    private final JTextField roomField = field("room1");

    public MenuWindow(Runnable onLocal, OnlineLauncher onOnline) {
        super("Big 2");

        BackgroundPanel root = new BackgroundPanel(new BorderLayout());
        setContentPane(root);
        root.add(new TitleArt(), BorderLayout.NORTH);

        CardLayout pages = new CardLayout();
        JPanel holder = new JPanel(pages);
        holder.setOpaque(false);
        holder.add(menuPage(onLocal, () -> pages.show(holder, "online")), "menu");
        holder.add(onlinePage(onOnline, () -> pages.show(holder, "menu")), "online");
        root.add(holder, BorderLayout.CENTER);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(680, 660);
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private JPanel menuPage(Runnable onLocal, Runnable onFriends) {
        ActionButton bots = new ActionButton("Play against bots", true);
        ActionButton friends = new ActionButton("Play with friends", false);
        ActionButton quit = new ActionButton("Quit", false);
        for (ActionButton b : new ActionButton[]{bots, friends, quit}) {
            b.setFont(Theme.font(Font.BOLD, 16f));
            b.setPreferredSize(new Dimension(280, 50));
        }
        bots.addActionListener(e -> onLocal.run());
        friends.addActionListener(e -> onFriends.run());
        quit.addActionListener(e -> System.exit(0));

        JPanel column = new JPanel(new GridLayout(0, 1, 0, 12));
        column.setOpaque(false);
        column.add(bots);
        column.add(friends);
        column.add(quit);

        JPanel page = new JPanel(new GridBagLayout());
        page.setOpaque(false);
        page.add(column);
        return page;
    }

    private JPanel onlinePage(OnlineLauncher onOnline, Runnable onBack) {
        JPanel form = new FormPanel();
        form.setBorder(BorderFactory.createEmptyBorder(22, 28, 20, 28));
        addRow(form, 0, "Your name", nameField);
        addRow(form, 1, "Server address", hostField);
        addRow(form, 2, "Port", portField);
        addRow(form, 3, "Room", roomField);

        JLabel hint = new JLabel("<html><div style='width:330px'>One player runs the server and shares its "
                + "address. Everyone enters the same room, then presses <b>Start game</b>. "
                + "Empty seats are taken by bots.</div></html>");
        hint.setFont(Theme.font(Font.PLAIN, 13f));
        hint.setForeground(Theme.TEXT_DIM);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        c.insets = new Insets(14, 0, 0, 0);
        form.add(hint, c);

        ActionButton join = new ActionButton("Join", true);
        ActionButton back = new ActionButton("Back", false);
        join.addActionListener(e -> {
            String name = nameField.getText().trim().replaceAll("\\s+", "_");
            String room = roomField.getText().trim().replaceAll("\\s+", "_");
            String host = hostField.getText().trim();
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "The port must be a number, for example 5050.");
                return;
            }
            if (name.isEmpty() || room.isEmpty() || host.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter your name, the server address and a room.");
                return;
            }
            onOnline.launch(host, port, room, name);
        });
        back.addActionListener(e -> onBack.run());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.add(join);
        buttons.add(back);

        JPanel page = new JPanel(new GridBagLayout());
        page.setOpaque(false);
        GridBagConstraints p = new GridBagConstraints();
        p.gridy = 0;
        page.add(form, p);
        p.gridy = 1;
        p.insets = new Insets(18, 0, 0, 0);
        page.add(buttons, p);
        return page;
    }

    private static void addRow(JPanel form, int row, String label, JTextField field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(5, 0, 5, 14);
        c.anchor = GridBagConstraints.WEST;
        JLabel l = new JLabel(label);
        l.setFont(Theme.font(Font.PLAIN, 14f));
        l.setForeground(Theme.TEXT_DIM);
        form.add(l, c);
        c.insets = new Insets(5, 0, 5, 0);
        form.add(field, c);
    }

    private static JTextField field(String text) {
        JTextField f = new JTextField(text, 16);
        f.setFont(Theme.font(Font.PLAIN, 15f));
        f.setForeground(Theme.TEXT);
        f.setBackground(new Color(0x0F1318));
        f.setCaretColor(Theme.GOLD);
        f.setSelectionColor(new Color(0x5A4A22));
        f.setSelectedTextColor(Theme.TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        return f;
    }

    /** A dark panel with a thin gold edge behind the form. */
    private static final class FormPanel extends JPanel {
        FormPanel() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            Theme.fillStroked(g, new RoundRectangle2D.Double(1, 1, getWidth() - 3, getHeight() - 3, 14, 14),
                    Theme.PANEL, new Color(201, 162, 75, 110), 1.2f);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    /** "BIG 2" in gold serif, a fine rule with a diamond, and the subtitle in spaced capitals. */
    private static final class TitleArt extends JComponent {
        TitleArt() {
            setPreferredSize(new Dimension(680, 230));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            int w = getWidth();
            double mid = w / 2.0;

            Suit[] suits = {Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS};
            for (int i = 0; i < suits.length; i++) {
                Theme.drawSuit(g, suits[i], mid - 54 + i * 36, 46, 18, new Color(201, 162, 75, 150));
            }

            g.setFont(Theme.serif(Font.BOLD, 88f));
            FontMetrics fm = g.getFontMetrics();
            String title = "BIG 2";
            float tx = (float) (mid - fm.stringWidth(title) / 2.0);
            g.setColor(new Color(0, 0, 0, 120));
            g.drawString(title, tx + 2, 148);
            g.setColor(Theme.GOLD);
            g.drawString(title, tx, 145);

            g.setColor(new Color(201, 162, 75, 140));
            g.fillRect((int) mid - 130, 170, 112, 1);
            g.fillRect((int) mid + 18, 170, 112, 1);
            Path2D diamond = new Path2D.Double();
            diamond.moveTo(mid, 164);
            diamond.lineTo(mid + 6, 170.5);
            diamond.lineTo(mid, 177);
            diamond.lineTo(mid - 6, 170.5);
            diamond.closePath();
            g.setColor(Theme.GOLD);
            g.fill(diamond);

            g.setFont(Theme.font(Font.PLAIN, 13f));
            g.setColor(Theme.TEXT_DIM);
            String subtitle = "CHOI DAI DI";
            Theme.spacedText(g, subtitle, (float) (mid - Theme.spacedWidth(g, subtitle, 5f) / 2.0), 202f, 5f);
            g.dispose();
        }
    }
}
