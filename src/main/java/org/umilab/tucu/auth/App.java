package org.umilab.tucu.auth;

import org.umilab.tucu.auth.i18n.Messages;
import org.umilab.tucu.auth.ui.MainFrame;
import org.umilab.tucu.auth.ui.TucuTheme;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class App {

    public static void main(String[] args) {
        // Language: explicit user choice > OS locale detection (Spanish -> es, else English).
        Messages.init();
        // Branded FlatLaf title bar honoring the TitlePane.* tokens.
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        // Designer theme: dark by default, persisted user choice. Must run
        // before any window exists.
        TucuTheme.install();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
