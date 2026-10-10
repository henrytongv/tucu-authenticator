package org.umilab.tucu.auth.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Transient notification shown at the bottom of the parent window. */
public final class Toast {

    private Toast() {}

    public static void show(Component parent, String message) {
        JWindow win = new JWindow(SwingUtilities.getWindowAncestor(parent));
        win.setBackground(new Color(0, 0, 0, 0));

        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiKit.surface());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(UiKit.line());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        JLabel label = new JLabel(message);
        label.setForeground(UiKit.text());
        label.setFont(UiKit.ui(15, Font.PLAIN));
        bubble.add(label, BorderLayout.CENTER);

        win.add(bubble);
        win.pack();
        var owner = SwingUtilities.getWindowAncestor(parent);
        int x = owner.getX() + (owner.getWidth() - win.getWidth()) / 2;
        int y = owner.getY() + owner.getHeight() - win.getHeight() - 60;
        win.setLocation(x, y);
        win.setAlwaysOnTop(true);
        win.setVisible(true);
        Timer t = new Timer(2500, e -> win.dispose());
        t.setRepeats(false);
        t.start();
    }
}
