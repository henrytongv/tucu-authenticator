package org.umilab.tucu.auth.ui;

import org.umilab.tucu.auth.i18n.Messages;
import org.umilab.tucu.auth.otp.Totp;
import org.umilab.tucu.auth.util.Clipboards;
import org.umilab.tucu.auth.vault.VaultCodec;
import org.umilab.tucu.auth.vault.VaultEntry;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Main view: branded toolbar (search, theme, settings, import), a list of
 * account rows with grouped codes, per-row tide line, icon copy button with
 * in-row "Copied" confirmation, and a designed empty state.
 */
public final class VaultPanel extends JPanel {

    private static final int WARN_BEFORE_LOCK_S = 60;
    private static final int COPIED_MS = 1800;

    private final MainFrame frame;
    private final VaultCodec.Session session;

    private final JTextField search = new JTextField();
    private final JLabel countLabel = new JLabel();
    private final JPanel rowsPanel = new JPanel();
    private final JPanel listWrap = new JPanel(new CardLayout());
    private final List<EntryRow> rows = new ArrayList<>();

    private final Timer refreshTimer;
    private final Timer lockTimer;
    private int secondsToLock;
    private boolean warnShown = false;

    private int lockTimeoutS;
    private int clipboardClearS;

    public VaultPanel(MainFrame frame, VaultCodec.Session session) {
        super(new BorderLayout());
        this.frame = frame;
        this.session = session;
        this.lockTimeoutS = Settings.get().lockTimeoutSeconds;
        this.clipboardClearS = Settings.get().clipboardClearSeconds;
        setBackground(UiKit.panelBg());
        setOpaque(true);

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildList(), BorderLayout.CENTER);

        reload();

        refreshTimer = new Timer(400, e -> tick());
        refreshTimer.start();

        secondsToLock = lockTimeoutS;
        lockTimer = new Timer(1000, e -> tickLock());
        lockTimer.start();
    }

    // ------------------------------------------------------------------
    // toolbar
    // ------------------------------------------------------------------

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiKit.line()),
                BorderFactory.createEmptyBorder(12, 26, 12, 26)));
        bar.setBackground(UiKit.listBg());
        bar.setOpaque(true);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 14);
        c.fill = GridBagConstraints.VERTICAL;

        search.putClientProperty("JTextField.placeholderText", Messages.get("vault.search"));
        search.putClientProperty("JTextField.leadingIcon", UiKit.icon("search", 18, UiKit.text3()));
        search.putClientProperty("JTextField.showClearButton", true);
        search.setFont(UiKit.ui(15, Font.PLAIN));
        search.setPreferredSize(new Dimension(340, 38));
        search.getAccessibleContext().setAccessibleName(Messages.get("vault.search"));
        search.getDocument().addDocumentListener((SimpleDocumentListener) e -> rebuildRows());
        bar.add(search, c);

        c.weightx = 1;
        bar.add(Box.createHorizontalGlue(), c);
        c.weightx = 0;
        c.insets = new Insets(0, 8, 0, 0);

        JButton themeBtn = UiKit.iconButton("theme", Messages.get("vault.theme"));
        themeBtn.addActionListener(e -> TucuTheme.toggle());
        bar.add(themeBtn, c);

        JButton gearBtn = UiKit.iconButton("gear", Messages.get("vault.settings"));
        gearBtn.addActionListener(e -> openSettings());
        bar.add(gearBtn, c);

        c.insets = new Insets(0, 12, 0, 0);
        JButton importBtn = UiKit.primary(Messages.get("vault.import"));
        importBtn.setIcon(UiKit.icon("import", 17, UiKit.textOnBrand()));
        importBtn.addActionListener(e -> ImportDialog.show(this, this::importFile));
        bar.add(importBtn, c);

        return bar;
    }

    // ------------------------------------------------------------------
    // list header + rows + empty state
    // ------------------------------------------------------------------

    private JPanel buildList() {
        JPanel listPage = new JPanel(new BorderLayout());
        listPage.setOpaque(false);

        JPanel head = new JPanel(new GridBagLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(22, 26, 10, 26));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.BASELINE_LEADING;

        JLabel title = UiKit.title(Messages.get("vault.title"), 21);
        head.add(title, c);
        c.insets = new Insets(0, 14, 0, 0);
        countLabel.setFont(UiKit.ui(15, Font.PLAIN));
        head.add(countLabel, c);

        c.weightx = 1;
        head.add(Box.createHorizontalGlue(), c);
        c.weightx = 0;
        JLabel hint = UiKit.tertiary(Messages.get("vault.hint"), 14);
        head.add(hint, c);
        listPage.add(head, BorderLayout.NORTH);

        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setOpaque(true);
        rowsPanel.setBackground(UiKit.listBg());

        JScrollPane scroll = new JScrollPane(rowsPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listPage.add(scroll, BorderLayout.CENTER);

        listWrap.add(listPage, "list");
        listWrap.add(buildEmptyState(), "empty");
        listWrap.setOpaque(false);
        return wrapInListPage(listWrap);
    }

    private JPanel wrapInListPage(JPanel content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildEmptyState() {
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 24, 0);
        center.add(UiKit.mark(92), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        center.add(UiKit.title(Messages.get("vault.empty.title"), 22), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 24, 0);
        JLabel desc = UiKit.secondary(
                "<div style='text-align:center;width:460px'>"
                        + Messages.get("vault.empty.desc") + "</div>", 16);
        desc.setHorizontalAlignment(JLabel.CENTER);
        center.add(desc, c);

        c.gridy++;
        JPanel acts = new JPanel();
        acts.setOpaque(false);
        JButton importBtn = UiKit.primary(Messages.get("vault.empty.import"));
        importBtn.addActionListener(e -> ImportDialog.show(this, this::importFile));
        JButton where = UiKit.ghost(Messages.get("vault.empty.where"));
        where.addActionListener(e -> JOptionPane.showMessageDialog(this,
                Messages.get("vault.empty.where.msg"),
                Messages.get("vault.empty.where"),
                JOptionPane.INFORMATION_MESSAGE));
        acts.add(importBtn);
        acts.add(where);
        center.add(acts, c);
        return center;
    }

    private void reload() {
        rebuildRows();
        tick();
    }

    private void rebuildRows() {
        rows.clear();
        rowsPanel.removeAll();
        String filter = search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        int total = 0;
        // favorites pinned on top; vault order preserved within each group
        for (VaultEntry e : sortedEntries()) {
            total++;
            if (!filter.isEmpty()
                    && !e.getName().toLowerCase(java.util.Locale.ROOT).contains(filter)
                    && !e.getIssuer().toLowerCase(java.util.Locale.ROOT).contains(filter)) {
                continue;
            }
            EntryRow row = new EntryRow(e);
            rows.add(row);
            rowsPanel.add(row);
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();
        countLabel.setText(String.valueOf(total));
        countLabel.setForeground(UiKit.text3());
        ((CardLayout) listWrap.getLayout()).show(listWrap, total == 0 ? "empty" : "list");
    }

    private List<VaultEntry> sortedEntries() {
        List<VaultEntry> favs = new ArrayList<>();
        List<VaultEntry> rest = new ArrayList<>();
        for (VaultEntry e : session.vault.getEntries()) {
            (e.isFavorite() ? favs : rest).add(e);
        }
        favs.addAll(rest);
        return favs;
    }

    // ------------------------------------------------------------------
    // timers
    // ------------------------------------------------------------------

    public void stopTimers() {
        refreshTimer.stop();
        lockTimer.stop();
    }

    /** Called when the app is re-unlocked after an auto-lock. */
    public void onUnlocked() {
        secondsToLock = lockTimeoutS;
        warnShown = false;
        lockTimer.restart();
        reload();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (EntryRow r : rows) r.tick(now);
    }

    private void tickLock() {
        if (--secondsToLock == WARN_BEFORE_LOCK_S && !warnShown) {
            warnShown = true;
            Toast.show(this, Messages.fmt("lock.warn", WARN_BEFORE_LOCK_S));
        }
        if (secondsToLock <= 0) {
            lockTimer.stop();
            frame.lock();
        }
    }

    private void openSettings() {
        SettingsDialog.show(frame, session);
        lockTimeoutS = Settings.get().lockTimeoutSeconds;
        clipboardClearS = Settings.get().clipboardClearSeconds;
        secondsToLock = lockTimeoutS;
    }

    // ------------------------------------------------------------------
    // import (file chosen by ImportDialog)
    // ------------------------------------------------------------------

    private void importFile(java.io.File f) {
        try {
            String json = java.nio.file.Files.readString(f.toPath());
            org.umilab.tucu.auth.vault.AegisImporter.Result res;
            boolean plaintext = !org.umilab.tucu.auth.vault.AegisImporter.isEncrypted(json);
            if (plaintext) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        Messages.fmt("import.warn.msg", f.getAbsolutePath()),
                        Messages.get("import.warn.title"),
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.OK_OPTION) return;
                res = new org.umilab.tucu.auth.vault.AegisImporter().parse(json);
            } else {
                // asks for the export password; null = user cancelled
                res = ImportPasswordDialog.show(this, json);
                if (res == null) return;
            }
            mergeImport(res);
            frame.getStore().write(VaultCodec.encryptDb(session, session.vault));
            reload();
            String msg = Messages.fmt("import.done", res.entries().size());
            if (res.skipped() > 0) msg += Messages.fmt("import.done.skipped", res.skipped());
            Toast.show(this, Messages.fmt("import.done", res.entries().size()));
            if (plaintext) {
                msg += Messages.fmt("import.done.reminder", f.getAbsolutePath());
            }
            JOptionPane.showMessageDialog(this, msg,
                    Messages.get("import.done.title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (org.umilab.tucu.auth.vault.AegisImporter.ImportException ex) {
            JOptionPane.showMessageDialog(this, Messages.get(ex.key()),
                    Messages.get("import.failed.title"), JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    Messages.fmt("import.failed", ex.getMessage()),
                    Messages.get("import.failed.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * UUID-replace merge. A locally favorited entry keeps its star when a
     * re-import delivers the same account without the flag, so imports can
     * only add favorites, never silently remove them.
     */
    private void mergeImport(org.umilab.tucu.auth.vault.AegisImporter.Result res) {
        for (VaultEntry e : res.entries()) {
            VaultEntry existing = session.vault.get(e.getUuid());
            if (existing != null && existing.isFavorite()) {
                e.setFavorite(true);
            }
            session.vault.put(e);
        }
    }

    // ==================================================================
    // Entry row: avatar | name/issuer | code + seconds | copy | tide line
    // ==================================================================

    private final class EntryRow extends JPanel {
        private final VaultEntry entry;
        private final JLabel codeLabel;
        private final JLabel secsLabel;
        private final JPanel copyWrap;
        private final JButton copyBtn;
        private boolean hovered;
        private int remaining = -1;
        private String lastCode = "";

        EntryRow(VaultEntry entry) {
            super(new GridBagLayout());
            this.entry = entry;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(12, 26, 18, 26));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);

            GridBagConstraints c = new GridBagConstraints();
            c.gridy = 0;
            c.anchor = GridBagConstraints.CENTER;

            // avatar
            c.insets = new Insets(0, 0, 0, 16);
            add(new UiKit.Avatar(seed(), 44), c);

            // name + issuer
            JPanel meta = new JPanel();
            meta.setOpaque(false);
            meta.setLayout(new BoxLayout(meta, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(entry.getName());
            name.setFont(UiKit.ui(16, Font.BOLD));
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            meta.add(name);
            if (!entry.getIssuer().isBlank()) {
                JLabel issuer = UiKit.tertiary(entry.getIssuer(), 14);
                issuer.setAlignmentX(Component.LEFT_ALIGNMENT);
                issuer.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
                meta.add(issuer);
            }
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, 0, 24);
            add(meta, c);
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;

            // code + seconds (right aligned)
            JPanel codeBox = new JPanel();
            codeBox.setOpaque(false);
            codeBox.setLayout(new BoxLayout(codeBox, BoxLayout.Y_AXIS));
            codeLabel = new JLabel();
            codeLabel.setFont(UiKit.mono(26, Font.BOLD));
            codeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            secsLabel = new JLabel();
            secsLabel.setFont(UiKit.ui(14, Font.PLAIN));
            secsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            secsLabel.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
            codeBox.add(codeLabel);
            codeBox.add(secsLabel);
            c.insets = new Insets(0, 0, 0, 12);
            add(codeBox, c);

            // star toggle: favorites stay pinned on top of the list
            JButton starBtn = new JButton() {
                @Override public void updateUI() {
                    super.updateUI();
                    setIcon(UiKit.icon(entry.isFavorite() ? "star-fill" : "star", 20,
                            entry.isFavorite() ? UiKit.brand() : UiKit.text3()));
                }
            };
            starBtn.setIcon(UiKit.icon(entry.isFavorite() ? "star-fill" : "star", 20,
                    entry.isFavorite() ? UiKit.brand() : UiKit.text3()));
            String starTip = Messages.get(entry.isFavorite()
                    ? "vault.fav.remove" : "vault.fav.add");
            starBtn.setToolTipText(starTip);
            starBtn.getAccessibleContext().setAccessibleName(starTip);
            starBtn.setPreferredSize(new Dimension(38, 38));
            starBtn.setContentAreaFilled(false);
            starBtn.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            starBtn.setFocusPainted(true);
            starBtn.addActionListener(e -> toggleFavorite());
            c.insets = new Insets(0, 0, 0, 6);
            add(starBtn, c);

            // copy icon <-> "Copied" confirmation, in the same row slot
            copyWrap = new JPanel(new CardLayout());
            copyWrap.setOpaque(false);
            copyWrap.setPreferredSize(new Dimension(104, 38));
            copyBtn = UiKit.iconButton("copy", Messages.get("vault.copy"));
            copyBtn.addActionListener(e -> copyCode());
            JLabel copied = new JLabel(Messages.get("vault.copied")) {
                @Override public void updateUI() {
                    super.updateUI();
                    setIcon(UiKit.icon("check", 17, UiKit.brand()));
                    setForeground(UiKit.brand());
                }
            };
            copied.setFont(UiKit.ui(15, Font.PLAIN));
            copied.setIcon(UiKit.icon("check", 17, UiKit.brand()));
            copied.setForeground(UiKit.brand());
            copied.setHorizontalAlignment(JLabel.RIGHT);
            copyWrap.add(copyBtn, "copy");
            copyWrap.add(copied, "copied");
            c.insets = new Insets(0, 0, 0, 0);
            add(copyWrap, c);

            // row click / keyboard activation both copy
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { copyCode(); }
            };
            addMouseListener(mouse);
            getAccessibleContext().setAccessibleName(
                    entry.getIssuer().isBlank() ? entry.getName()
                            : entry.getIssuer() + " " + entry.getName());
            getInputMap(WHEN_FOCUSED).put(
                    javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "copy");
            getInputMap(WHEN_FOCUSED).put(
                    javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "copy");
            getActionMap().put("copy", new javax.swing.AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { copyCode(); }
            });
            tick(System.currentTimeMillis());
        }

        private String seed() {
            return !entry.getIssuer().isBlank() ? entry.getIssuer() : entry.getName();
        }

        private void toggleFavorite() {
            entry.setFavorite(!entry.isFavorite());
            try {
                frame.getStore().write(VaultCodec.encryptDb(session, session.vault));
            } catch (Exception ex) {
                entry.setFavorite(!entry.isFavorite()); // roll back
                JOptionPane.showMessageDialog(VaultPanel.this,
                        Messages.fmt("import.failed", ex.getMessage()),
                        Messages.get("import.failed.title"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            rebuildRows();
        }

        void copyCode() {
            Clipboards.copy(Totp.code(entry.getSecret(), entry.getPeriod(),
                    System.currentTimeMillis()), clipboardClearS);
            ((CardLayout) copyWrap.getLayout()).show(copyWrap, "copied");
            Timer t = new Timer(COPIED_MS, e ->
                    ((CardLayout) copyWrap.getLayout()).show(copyWrap, "copy"));
            t.setRepeats(false);
            t.start();
        }

        void tick(long now) {
            int rem = Totp.secondsRemaining(entry.getPeriod(), now);
            String code = Totp.code(entry.getSecret(), entry.getPeriod(), now);
            if (!code.equals(lastCode)) {
                lastCode = code;
                codeLabel.setText(UiKit.groupCode(code));
            }
            remaining = rem;
            secsLabel.setText(Messages.fmt("vault.secs", rem));
            // calm urgency: dim the code and warm the seconds; never blink
            boolean expiring = rem <= 5;
            codeLabel.setForeground(expiring ? UiKit.text2() : UiKit.text());
            secsLabel.setForeground(rem <= 5 ? UiKit.urgent() : UiKit.text3());
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovered || hasFocus() ? UiKit.hover() : UiKit.listBg());
            g2.fillRect(0, 0, getWidth(), getHeight());
            // top hairline separating rows
            g2.setColor(UiKit.line());
            g2.fillRect(0, 0, getWidth(), 1);
            // focus ring inside the row
            if (hasFocus()) {
                g2.setColor(javax.swing.UIManager.getColor("Component.focusColor"));
                g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 8, 8);
            }
            // tide line: 2px at the bottom edge, drains left to right
            int period = entry.getPeriod();
            double frac = period <= 0 ? 0 : Math.max(0, Math.min(1, (double) remaining / period));
            int w = (int) Math.round(getWidth() * frac);
            g2.setColor(TucuTheme.tideColor(remaining < 0 ? period : remaining, period));
            g2.fillRect(0, getHeight() - 2, w, 2);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
