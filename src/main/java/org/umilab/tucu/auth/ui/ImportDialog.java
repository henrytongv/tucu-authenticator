package org.umilab.tucu.auth.ui;

import org.umilab.tucu.auth.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Import screen (design mockup 05): drag-and-drop zone + file chooser.
 * The chosen file is handed back to the caller, which owns the warning,
 * parsing and merge steps.
 */
public final class ImportDialog {

    private ImportDialog() {}

    public static void show(Component parent, Consumer<File> onFileChosen) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner instanceof JFrame f ? f : null,
                Messages.get("import.title"), true);
        if (owner != null) d.setIconImages(owner.getIconImages());

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(40, 48, 40, 48));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        content.add(centered(UiKit.title(Messages.get("import.title"), 21)), c);

        c.gridy++;
        c.insets = new Insets(10, 0, 26, 0);
        content.add(centered(UiKit.secondary(
                "<div style='text-align:center;width:520px'>"
                        + Messages.get("import.desc") + "</div>", 16)), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 22, 0);
        content.add(new DropZone(d, onFileChosen), c);

        c.gridy++;
        content.add(UiKit.noteBox("check", UiKit.brand(),
                Messages.get("import.formats")), c);

        d.setContentPane(content);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private static JPanel centered(JComponent inner) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.add(inner);
        return p;
    }

    /** Dashed-border drop target; also accepts clicks/keyboard via its button. */
    private static final class DropZone extends JPanel {
        DropZone(JDialog dialog, Consumer<File> onFileChosen) {
            setOpaque(false);
            setLayout(new GridBagLayout());
            setPreferredSize(new Dimension(600, 210));
            setBorder(BorderFactory.createEmptyBorder(28, 30, 28, 30));

            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;

            JLabel ic = new JLabel(UiKit.icon("upload", 52, UiKit.text3()));
            c.insets = new Insets(0, 0, 14, 0);
            add(ic, c);

            c.gridy++;
            c.insets = new Insets(0, 0, 6, 0);
            add(UiKit.title(Messages.get("import.drop.title"), 18), c);

            c.gridy++;
            c.insets = new Insets(0, 0, 18, 0);
            // plain (non-HTML) label: a width-constrained HTML view would wrap
            // every word onto its own line here
            JLabel sub = new JLabel(Messages.get("import.drop.sub"));
            sub.setFont(UiKit.ui(15, Font.PLAIN));
            sub.setForeground(UiKit.text3());
            add(sub, c);

            c.gridy++;
            c.insets = new Insets(0, 0, 0, 0);
            JButton choose = new JButton(Messages.get("import.drop.button"));
            choose.setFont(UiKit.ui(15, Font.PLAIN));
            choose.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiKit.line(), 1, true),
                    BorderFactory.createEmptyBorder(8, 16, 8, 16)));
            add(choose, c);

            Runnable pick = () -> {
                JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
                fc.setDialogTitle(Messages.get("import.title"));
                if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                    dialog.dispose();
                    onFileChosen.accept(fc.getSelectedFile());
                }
            };
            choose.addActionListener(e -> pick.run());

            // drag & drop file import
            setTransferHandler(new TransferHandler() {
                @Override public boolean canImport(TransferSupport s) {
                    return s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                }
                @Override @SuppressWarnings("unchecked")
                public boolean importData(TransferSupport s) {
                    try {
                        List<File> files = (List<File>) s.getTransferable()
                                .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                        if (!files.isEmpty()) {
                            dialog.dispose();
                            onFileChosen.accept(files.get(0));
                            return true;
                        }
                    } catch (Exception ignored) {
                    }
                    return false;
                }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UiKit.listBg());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(UiKit.line());
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                    0, new float[]{6, 6}, 0));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
