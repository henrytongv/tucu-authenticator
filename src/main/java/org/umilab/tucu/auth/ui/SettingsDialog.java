package org.umilab.tucu.auth.ui;

import org.umilab.tucu.auth.crypto.PasswordPolicy;
import org.umilab.tucu.auth.i18n.Messages;
import org.umilab.tucu.auth.vault.VaultCodec;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Arrays;

/**
 * Settings: Security (auto-lock, clipboard delay, master password change).
 * Shown as a modal dialog centered on the main window.
 */
public final class SettingsDialog {

    private SettingsDialog() {}

    public static void show(MainFrame owner, VaultCodec.Session session) {
        JDialog d = new JDialog(owner, Messages.get("settings.title"), true);
        d.setIconImages(owner.getIconImages());

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(26, 30, 22, 30));

        // ---------------- Security ----------------
        p.add(sectionHeader(Messages.get("settings.security"), null));
        p.add(Box.createVerticalStrut(14));

        JSpinner lockSpin = new JSpinner(new SpinnerNumberModel(
                Settings.get().lockTimeoutSeconds / 60, 1, 240, 1));
        lockSpin.setFont(UiKit.ui(15, Font.PLAIN));
        lockSpin.setPreferredSize(new Dimension(90, 38));
        p.add(setting(Messages.get("settings.autolock"),
                Messages.get("settings.autolock.sub"),
                withUnit(lockSpin, Messages.get("settings.minutes"))));

        JSpinner clipSpin = new JSpinner(new SpinnerNumberModel(
                Settings.get().clipboardClearSeconds, 5, 300, 5));
        clipSpin.setFont(UiKit.ui(15, Font.PLAIN));
        clipSpin.setPreferredSize(new Dimension(90, 38));
        p.add(setting(Messages.get("settings.clipboard"),
                Messages.get("settings.clipboard.sub"),
                withUnit(clipSpin, Messages.get("settings.seconds"))));

        // ---------------- Master password ----------------
        p.add(sectionHeader(Messages.get("settings.password"),
                Messages.get("settings.password.sub")));
        p.add(Box.createVerticalStrut(12));

        JPasswordField cur = pwField(Messages.get("settings.pw.current"));
        JPasswordField nw = pwField(Messages.get("settings.pw.new"));
        JPasswordField conf = pwField(Messages.get("settings.pw.confirm"));
        p.add(pwRow(cur, Messages.get("settings.pw.current")));
        p.add(Box.createVerticalStrut(8));
        p.add(pwRow(nw, Messages.get("settings.pw.new")));
        p.add(Box.createVerticalStrut(8));
        p.add(pwRow(conf, Messages.get("settings.pw.confirm")));
        p.add(Box.createVerticalStrut(18));

        // ---------------- actions ----------------
        JPanel acts = new JPanel();
        acts.setOpaque(false);
        acts.setLayout(new BoxLayout(acts, BoxLayout.X_AXIS));
        acts.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton save = UiKit.primary(Messages.get("settings.save"));
        JButton close = UiKit.ghost(Messages.get("settings.close"));
        close.addActionListener(e -> d.dispose());
        acts.add(save);
        acts.add(Box.createHorizontalStrut(10));
        acts.add(close);
        p.add(acts);

        d.setContentPane(p);
        d.pack();
        d.setResizable(false);
        // center on the owner window (explicit, reliable with decorated dialogs)
        java.awt.Point o = owner.getLocationOnScreen();
        d.setLocation(o.x + Math.max(0, (owner.getWidth() - d.getWidth()) / 2),
                      o.y + Math.max(0, (owner.getHeight() - d.getHeight()) / 2));

        save.addActionListener(e -> {
            Settings.get().lockTimeoutSeconds = ((Number) lockSpin.getValue()).intValue() * 60;
            Settings.get().clipboardClearSeconds = ((Number) clipSpin.getValue()).intValue();

            char[] curP = cur.getPassword();
            char[] newP = nw.getPassword();
            char[] confP = conf.getPassword();
            boolean wantsChange = curP.length > 0 || newP.length > 0 || confP.length > 0;
            if (!wantsChange) {
                d.dispose();
                return;
            }
            String problem = PasswordPolicy.validate(newP);
            if (problem != null) {
                wipeAll(curP, newP, confP);
                JOptionPane.showMessageDialog(d, Messages.get(problem),
                        Messages.get("settings.password"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!Arrays.equals(newP, confP)) {
                wipeAll(curP, newP, confP);
                JOptionPane.showMessageDialog(d, Messages.get("pw.err.mismatch"),
                        Messages.get("settings.password"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            save.setEnabled(false);
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() throws Exception {
                    VaultCodec.Session check =
                            VaultCodec.open(owner.getStore().read(), curP);
                    check.wipe();
                    owner.getStore().write(VaultCodec.rewrap(session, newP, session.vault));
                    return true;
                }
                @Override protected void done() {
                    wipeAll(curP, newP, confP);
                    try {
                        get();
                        Toast.show(d, Messages.get("settings.pw.changed"));
                        d.dispose();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(d,
                                Messages.get("settings.pw.wrong"),
                                Messages.get("settings.password"), JOptionPane.ERROR_MESSAGE);
                        save.setEnabled(true);
                    }
                }
            }.execute();
        });

        d.setVisible(true);
    }

    // ---- helpers ----

    private static JPanel sectionHeader(String title, String subtitle) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JLabel t = UiKit.title(title, 18);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(t);
        if (subtitle != null) {
            JLabel s = UiKit.tertiary(subtitle, 14);
            s.setAlignmentX(Component.LEFT_ALIGNMENT);
            s.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            p.add(s);
        }
        return p;
    }

    private static JPanel setting(String title, String subtitle, JComponent control) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiKit.line()),
                BorderFactory.createEmptyBorder(14, 0, 16, 0)));
        JLabel t = new JLabel(title);
        t.setFont(UiKit.ui(16, Font.BOLD));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(t);
        JLabel s = UiKit.tertiary(subtitle, 14);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        p.add(s);
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel cp = new JPanel();
        cp.setOpaque(false);
        cp.setLayout(new BoxLayout(cp, BoxLayout.X_AXIS));
        cp.setAlignmentX(Component.LEFT_ALIGNMENT);
        cp.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        cp.add(control);
        cp.add(Box.createHorizontalGlue());
        p.add(cp);
        return p;
    }

    private static JPanel withUnit(JSpinner spinner, String unit) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.add(spinner);
        p.add(Box.createHorizontalStrut(10));
        p.add(UiKit.tertiary(unit, 14));
        return p;
    }

    private static JPasswordField pwField(String accessibleName) {
        JPasswordField f = new JPasswordField();
        f.setFont(UiKit.mono(15, Font.PLAIN));
        f.setPreferredSize(new Dimension(280, 40));
        f.setMaximumSize(new Dimension(280, 40));
        f.getAccessibleContext().setAccessibleName(accessibleName);
        return f;
    }

    private static JPanel pwRow(JPasswordField field, String label) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(label);
        l.setFont(UiKit.ui(15, Font.PLAIN));
        l.setPreferredSize(new Dimension(210, 24));
        l.setLabelFor(field);
        p.add(l);
        p.add(field);
        p.add(Box.createHorizontalGlue());
        return p;
    }

    private static void wipeAll(char[]... arrs) {
        for (char[] a : arrs) Arrays.fill(a, ' ');
    }
}
