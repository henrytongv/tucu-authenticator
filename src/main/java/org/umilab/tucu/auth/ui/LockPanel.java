package org.umilab.tucu.auth.ui;

import org.umilab.tucu.auth.i18n.Messages;
import org.umilab.tucu.auth.vault.VaultCodec;

/**
 * Auto-lock screen: same password gate as unlock, but the verified
 * session is discarded (the live session is kept) and the vault view
 * is restored. All entry data is hidden while this card is shown.
 */
public final class LockPanel extends UnlockPanel {

    public LockPanel(MainFrame frame) {
        super(frame);
    }

    @Override protected String titleKey() { return Messages.get("unlock.title"); }
    @Override protected String subtitleKey() { return Messages.get("lock.desc"); }

    @Override protected VaultCodec.Session attempt(MainFrame frame, char[] password) throws Exception {
        return frame.verifyPassword(password);
    }

    @Override protected void onSuccess(MainFrame frame, VaultCodec.Session verified) {
        verified.wipe(); // throwaway session used only to prove the password
        frame.onReUnlocked();
    }
}
