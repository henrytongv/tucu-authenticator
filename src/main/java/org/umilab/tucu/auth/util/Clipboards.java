package org.umilab.tucu.auth.util;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Copies to the system clipboard and auto-clears it after a delay —
 * but only if the clipboard still holds the value we put there, so the
 * user's unrelated clipboard content is never destroyed.
 */
public final class Clipboards {

    private static final Timer TIMER = new Timer("clipboard-clear", true);

    private Clipboards() {}

    public static void copy(String text, int clearAfterSeconds) {
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(new StringSelection(text), null);
        TIMER.schedule(new TimerTask() {
            @Override public void run() {
                try {
                    Object content = cb.getData(DataFlavor.stringFlavor);
                    if (text.equals(content)) {
                        cb.setContents(new StringSelection(""), null);
                    }
                } catch (Exception ignored) {
                    // clipboard busy or content changed — nothing to clear
                }
            }
        }, clearAfterSeconds * 1000L);
    }
}
