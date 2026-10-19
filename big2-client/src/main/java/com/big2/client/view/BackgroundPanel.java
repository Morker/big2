package com.big2.client.view;

import javax.swing.JPanel;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;

/** A dark charcoal gradient behind every screen. */
final class BackgroundPanel extends JPanel {

    BackgroundPanel(LayoutManager layout) {
        super(layout);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setPaint(new GradientPaint(0, 0, Theme.BG_TOP, 0, getHeight(), Theme.BG_BOTTOM));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }
}
