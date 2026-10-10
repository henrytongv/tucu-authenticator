package org.umilab.tucu.auth.ui;

import org.umilab.tucu.auth.i18n.Messages;
import org.umilab.tucu.auth.vault.VaultCodec;
import org.umilab.tucu.auth.vault.VaultStore;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level frame. Card layout switches between:
 *   CREATE  - first launch, master password creation
 *   UNLOCK  - vault exists, password required before anything works
 *   VAULT   - the account list
 *   LOCK    - auto-locked; identical to unlock but the vault stays loaded
 */
public final class MainFrame extends JFrame {

    public static final String CARD_CREATE = "create";
    public static final String CARD_UNLOCK = "unlock";
    public static final String CARD_VAULT = "vault";
    public static final String CARD_LOCK = "lock";

    private final VaultStore store = new VaultStore();
    private final JPanel cards = new JPanel(new CardLayout());
    private VaultCodec.Session session;
    private VaultPanel vaultPanel;
    private boolean locked;

    public MainFrame() {
        super(Messages.get("app.title"));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setIconImages(appIcons());
        setSize(1040, 640);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        setContentPane(cards);
        route();
    }

    private static List<Image> appIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 20, 24, 32, 40, 48, 64, 128, 256, 512}) {
            java.net.URL url = MainFrame.class.getResource("/logo/app-icon/tucu-icon-" + size + ".png");
            if (url != null) icons.add(new ImageIcon(url).getImage());
        }
        return icons;
    }

    private void route() {
        cards.removeAll();
        vaultPanel = null;
        if (!store.exists()) {
            cards.add(new CreatePasswordPanel(this), CARD_CREATE);
            show(CARD_CREATE);
        } else {
            cards.add(new UnlockPanel(this), CARD_UNLOCK);
            show(CARD_UNLOCK);
        }
    }

    public void show(String card) {
        ((CardLayout) cards.getLayout()).show(cards, card);
        cards.revalidate();
        cards.repaint();
    }

    public VaultStore getStore() { return store; }

    public VaultCodec.Session getSession() { return session; }

    /** Called after successful vault creation or unlock. */
    public void onUnlocked(VaultCodec.Session session) {
        this.session = session;
        locked = false;
        vaultPanel = new VaultPanel(this, session);
        cards.add(vaultPanel, CARD_VAULT);
        show(CARD_VAULT);
    }

    /** Auto-lock: session kept, but all data hidden behind the lock screen. */
    public void lock() {
        if (session == null) return;
        locked = true;
        cards.add(new LockPanel(this), CARD_LOCK);
        show(CARD_LOCK);
    }

    /** Called when the lock screen password is verified. */
    public void onReUnlocked() {
        locked = false;
        show(CARD_VAULT);
        if (vaultPanel != null) vaultPanel.onUnlocked();
    }

    /** Rebuilds visible screens after a language change; session state is kept. */
    public void applyLanguage() {
        setTitle(Messages.get("app.title"));
        if (session == null) {
            route();
            return;
        }
        if (vaultPanel != null) vaultPanel.stopTimers();
        cards.removeAll();
        vaultPanel = new VaultPanel(this, session);
        cards.add(vaultPanel, CARD_VAULT);
        if (locked) {
            cards.add(new LockPanel(this), CARD_LOCK);
            show(CARD_LOCK);
        } else {
            show(CARD_VAULT);
        }
    }

    /** Verifies a password against the stored vault without creating a new session. */
    public VaultCodec.Session verifyPassword(char[] password) throws GeneralSecurityException {
        try {
            return VaultCodec.open(store.read(), password);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Could not read vault file", e);
        }
    }

    private void shutdown() {
        if (vaultPanel != null) vaultPanel.stopTimers();
        if (session != null) session.wipe();
        session = null;
        dispose();
        System.exit(0);
    }
}
