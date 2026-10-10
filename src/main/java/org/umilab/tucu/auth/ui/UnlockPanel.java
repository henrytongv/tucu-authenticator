package org.umilab.tucu.auth.ui;

import org.umilab.tucu.auth.i18n.Messages;
import org.umilab.tucu.auth.vault.VaultCodec;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

/**
 * Unlock gate (design mockup 04): centered mark, heading, single wide
 * password field, inline error under the field, primary + ghost actions.
 * Failed attempts back off exponentially.
 */
public class UnlockPanel extends JPanel {

    private int failures = 0;
    private final JPanel errorRow = new JPanel();
    private final JLabel errorText = new JLabel();
    private final JButton unlock;

    public UnlockPanel(MainFrame frame) {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 24, 0);
        add(UiKit.mark(92), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        add(UiKit.title(titleKey(), 22), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 22, 0);
        add(UiKit.secondary(subtitleKey(), 16), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 8, 0);
        JPasswordField pass = new JPasswordField();
        pass.setFont(UiKit.mono(16, Font.PLAIN));
        pass.setPreferredSize(new Dimension(420, 48));
        pass.putClientProperty("JTextField.placeholderText", Messages.get("create.password"));
        pass.getAccessibleContext().setAccessibleName(Messages.get("create.password"));
        add(pass, c);

        // inline error under the field — hidden until the first failure
        c.gridy++;
        c.insets = new Insets(0, 0, 14, 0);
        errorRow.setOpaque(false);
        errorRow.setLayout(new javax.swing.BoxLayout(errorRow, javax.swing.BoxLayout.X_AXIS));
        errorRow.setPreferredSize(new Dimension(420, 24));
        JLabel warnIcon = new JLabel(UiKit.icon("warn", 18, UiKit.urgent()));
        warnIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        errorText.setFont(UiKit.ui(15, Font.PLAIN));
        errorText.setForeground(UiKit.urgent());
        errorRow.add(warnIcon);
        errorRow.add(errorText);
        errorRow.setVisible(false);
        add(errorRow, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        JPanel acts = new JPanel();
        acts.setOpaque(false);
        unlock = UiKit.primary(Messages.get("unlock.button"));
        JButton forgot = UiKit.ghost(Messages.get("unlock.forgot"));
        forgot.addActionListener(e -> JOptionPane.showMessageDialog(this,
                Messages.fmt("forgot.msg", frame.getStore().getFile().toAbsolutePath()),
                Messages.get("forgot.title"), JOptionPane.INFORMATION_MESSAGE));
        acts.add(unlock);
        acts.add(forgot);
        add(acts, c);

        javax.swing.SwingUtilities.invokeLater(() -> {
            if (getRootPane() != null) getRootPane().setDefaultButton(unlock);
            pass.requestFocusInWindow();
        });

        unlock.addActionListener(e -> {
            char[] p = pass.getPassword();
            pass.setText("");
            unlock.setEnabled(false);
            showError(Messages.get("unlock.unlocking"), false);
            new SwingWorker<VaultCodec.Session, Void>() {
                @Override protected VaultCodec.Session doInBackground() throws Exception {
                    return attempt(frame, p);
                }
                @Override protected void done() {
                    Arrays.fill(p, ' ');
                    try {
                        onSuccess(frame, get());
                    } catch (Exception ex) {
                        failures++;
                        int wait = Math.min(60, 1 << Math.min(failures, 6));
                        showError(failures == 1
                                        ? Messages.get("unlock.error")
                                        : Messages.fmt("unlock.wait", wait), true);
                        Timer t = new Timer(wait * 1000, ev -> {
                            unlock.setEnabled(true);
                            errorRow.setVisible(false);
                        });
                        t.setRepeats(false);
                        t.start();
                        // keep the button enabled for the first failure message too,
                        // but enforce the backoff delay before re-enabling
                    }
                }
            }.execute();
        });
    }

    private void showError(String text, boolean asError) {
        errorText.setText(text);
        errorText.setForeground(asError ? UiKit.urgent() : UiKit.text3());
        errorRow.setVisible(true);
    }

    protected String titleKey() { return Messages.get("unlock.title"); }
    protected String subtitleKey() { return Messages.get("unlock.desc"); }

    /** Decrypt the stored vault — returns a fresh session. */
    protected VaultCodec.Session attempt(MainFrame frame, char[] password) throws Exception {
        return VaultCodec.open(frame.getStore().read(), password);
    }

    /** Startup unlock: adopt the session and show the vault. */
    protected void onSuccess(MainFrame frame, VaultCodec.Session session) {
        frame.onUnlocked(session);
    }
}
