package org.umilab.tucu.auth.ui;

import org.umilab.tucu.auth.crypto.PasswordPolicy;
import org.umilab.tucu.auth.i18n.Messages;
import org.umilab.tucu.auth.vault.Vault;
import org.umilab.tucu.auth.vault.VaultCodec;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

/**
 * First launch (design mockup 03): centered card with master password
 * creation — fields with labels on top, 4-segment strength meter with a
 * word + actionable hint, warning note box, primary action.
 */
public final class CreatePasswordPanel extends JPanel {

    public CreatePasswordPanel(MainFrame frame) {
        setLayout(new GridBagLayout());
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        // width comes from the children (fields are 560 px); height is the
        // natural sum of the components — do NOT set a fixed preferred size.
        card.setBorder(BorderFactory.createEmptyBorder(46, 0, 40, 0));
        GridBagConstraints wrap = new GridBagConstraints();
        wrap.weightx = 1;
        wrap.weighty = 1;
        wrap.anchor = GridBagConstraints.CENTER;
        add(card, wrap);

        JLabel title = UiKit.title(Messages.get("create.title"), 25);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        card.add(vgap(10));

        JLabel desc = UiKit.secondary(
                "<div style='width:540px'>" + Messages.get("create.desc") + "</div>", 16);
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(desc);
        card.add(vgap(26));

        // ---- master password field ----
        card.add(fieldLabel(Messages.get("create.password")));
        card.add(vgap(8));
        JPasswordField pass = passwordField();
        card.add(pass);
        card.add(vgap(7));
        card.add(tertiaryLine(Messages.get("create.hint")));
        card.add(vgap(12));

        // ---- strength meter + word + hint ----
        UiKit.StrengthMeter meter = new UiKit.StrengthMeter();
        meter.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(meter);
        card.add(vgap(8));
        JPanel strengthLine = new JPanel();
        strengthLine.setOpaque(false);
        strengthLine.setLayout(new BoxLayout(strengthLine, BoxLayout.X_AXIS));
        strengthLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel strengthWord = new JLabel(" ");
        strengthWord.setFont(UiKit.ui(15, Font.BOLD));
        JLabel strengthWhy = UiKit.tertiary(Messages.get("strength.hint"), 14);
        strengthWhy.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        strengthLine.add(strengthWord);
        strengthLine.add(strengthWhy);
        card.add(strengthLine);
        card.add(vgap(24));

        // ---- repeat field ----
        card.add(fieldLabel(Messages.get("create.repeat")));
        card.add(vgap(8));
        JPasswordField conf = passwordField();
        card.add(conf);
        card.add(vgap(20));

        // ---- warning note box ----
        JComponent note = UiKit.noteBox("warn", UiKit.warm(),
                "<div style='width:490px'>" + Messages.get("create.note") + "</div>");
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(note);
        card.add(vgap(16));

        // ---- inline error ----
        JLabel error = new JLabel(" ");
        error.setFont(UiKit.ui(15, Font.PLAIN));
        error.setForeground(UiKit.urgent());
        error.setAlignmentX(Component.LEFT_ALIGNMENT);
        error.getAccessibleContext().setAccessibleName("Password error");
        card.add(error);
        card.add(vgap(10));

        // ---- actions ----
        JPanel acts = new JPanel();
        acts.setOpaque(false);
        acts.setLayout(new BoxLayout(acts, BoxLayout.X_AXIS));
        acts.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton create = UiKit.primary(Messages.get("create.button"));
        create.getAccessibleContext().setAccessibleDescription(
                Messages.get("create.button"));
        acts.add(create);
        acts.add(Box.createHorizontalStrut(14));
        acts.add(UiKit.tertiary(Messages.get("create.caption"), 14));
        card.add(acts);

        javax.swing.SwingUtilities.invokeLater(() -> {
            if (getRootPane() != null) getRootPane().setDefaultButton(create);
        });

        // live strength feedback
        pass.getDocument().addDocumentListener((SimpleDocumentListener) e -> {
            int s = PasswordPolicy.strength(pass.getPassword());
            meter.setScore(s);
            strengthWord.setText(switch (s) {
                case 0, 1 -> s == 0 && pass.getPassword().length == 0 ? " "
                        : Messages.get("strength.weak");
                case 2 -> Messages.get("strength.fair");
                case 3 -> Messages.get("strength.good");
                default -> Messages.get("strength.strong");
            });
            strengthWord.setForeground(s <= 1 ? UiKit.urgent()
                    : s == 2 ? UiKit.warm() : UiKit.brand());
        });

        create.addActionListener(e -> {
            char[] p1 = pass.getPassword();
            char[] p2 = conf.getPassword();
            try {
                String problem = PasswordPolicy.validate(p1);
                if (problem != null) {
                    error.setText(Messages.get(problem));
                    return;
                }
                if (!Arrays.equals(p1, p2)) {
                    error.setText(Messages.get("pw.err.mismatch"));
                    return;
                }
                create.setEnabled(false);
                error.setText(Messages.get("create.deriving"));
                new SwingWorker<VaultCodec.Session, Void>() {
                    @Override protected VaultCodec.Session doInBackground() throws Exception {
                        VaultCodec.Session s = VaultCodec.newSession(p1);
                        frame.getStore().write(VaultCodec.encryptDb(s, new Vault()));
                        return s;
                    }
                    @Override protected void done() {
                        Arrays.fill(p1, ' ');
                        Arrays.fill(p2, ' ');
                        try {
                            frame.onUnlocked(get());
                        } catch (Exception ex) {
                            error.setText(Messages.fmt("create.failed", ex.getMessage()));
                            create.setEnabled(true);
                        }
                    }
                }.execute();
            } catch (Exception ex) {
                Arrays.fill(p1, ' ');
                Arrays.fill(p2, ' ');
                error.setText(Messages.fmt("create.failed", ex.getMessage()));
            }
        });
    }

    private static Component vgap(int h) { return Box.createVerticalStrut(h); }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UiKit.ui(15, Font.BOLD));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel tertiaryLine(String text) {
        JLabel l = UiKit.tertiary(text, 14);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JPasswordField passwordField() {
        JPasswordField f = new JPasswordField();
        f.setFont(UiKit.mono(16, Font.PLAIN));
        f.setPreferredSize(new Dimension(560, 48));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        return f;
    }
}
