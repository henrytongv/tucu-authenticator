package org.umilab.tucu.auth.ui;

import org.umilab.tucu.auth.i18n.Messages;
import org.umilab.tucu.auth.vault.AegisImporter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Modal prompt for the password of an encrypted Aegis export. The password
 * is used only to decrypt the file for this import; it is never stored and
 * the field content is wiped on close.
 */
public final class ImportPasswordDialog {

    private ImportPasswordDialog() {}

    /** @return the import result, or null if the user cancelled. */
    public static AegisImporter.Result show(Component parent, String json) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner instanceof JFrame f ? f : null,
                Messages.get("import.enc.title"), true);
        if (owner != null) d.setIconImages(owner.getIconImages());

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(34, 40, 30, 40));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel ic = new JLabel(UiKit.icon("lock", 34, UiKit.brand()));
        ic.setHorizontalAlignment(JLabel.CENTER);
        c.insets = new Insets(0, 0, 12, 0);
        content.add(ic, c);

        JLabel title = UiKit.title(Messages.get("import.enc.title"), 19);
        title.setHorizontalAlignment(JLabel.CENTER);
        c.gridy++;
        c.insets = new Insets(0, 0, 8, 0);
        content.add(title, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 20, 0);
        content.add(UiKit.secondary(
                "<div style='text-align:center;width:400px'>"
                        + Messages.get("import.enc.desc") + "</div>", 15), c);

        JLabel fieldLabel = UiKit.secondary(Messages.get("import.enc.password"), 14);
        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        content.add(fieldLabel, c);

        JPasswordField field = new JPasswordField();
        field.setFont(UiKit.mono(16, Font.PLAIN));
        field.setPreferredSize(new Dimension(400, 40));
        field.getAccessibleContext().setAccessibleName(Messages.get("import.enc.password"));
        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        content.add(field, c);

        JLabel err = new JLabel();
        err.setFont(UiKit.ui(14, Font.PLAIN));
        err.setIcon(UiKit.icon("warn", 16, UiKit.urgent()));
        err.setForeground(UiKit.urgent());
        err.setVisible(false);
        c.gridy++;
        c.insets = new Insets(0, 0, 14, 0);
        content.add(err, c);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        JButton cancel = UiKit.ghost(Messages.get("import.enc.cancel"));
        JButton open = UiKit.primary(Messages.get("import.enc.button"));
        buttons.add(cancel);
        buttons.add(open);
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        content.add(buttons, c);

        AtomicReference<AegisImporter.Result> result = new AtomicReference<>();
        Runnable attempt = () -> {
            char[] pw = field.getPassword();
            try {
                AegisImporter.Result res = new AegisImporter().parseEncrypted(json, pw);
                result.set(res);
                d.dispose();
            } catch (AegisImporter.ImportException ex) {
                err.setText(Messages.get(ex.key()));
                err.setVisible(true);
                field.selectAll();
                field.requestFocusInWindow();
                d.pack();
            } finally {
                Arrays.fill(pw, '\0');
            }
        };
        open.addActionListener(e -> attempt.run());
        cancel.addActionListener(e -> d.dispose());

        d.setContentPane(content);
        d.getRootPane().setDefaultButton(open);
        d.pack();
        d.setResizable(false);
        // center explicitly on the owner window (setLocationRelativeTo can be
        // off under FlatLaf's custom title bar)
        if (owner != null) {
            d.setLocation(
                    owner.getX() + (owner.getWidth() - d.getWidth()) / 2,
                    owner.getY() + (owner.getHeight() - d.getHeight()) / 2);
        } else {
            d.setLocationRelativeTo(null);
        }
        d.setVisible(true);
        return result.get();
    }
}
