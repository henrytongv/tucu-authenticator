package org.umilab.tucu.auth.ui;

/** In-memory app settings. Defaults per spec; adjustable in the Settings dialog. */
public final class Settings {

    private static final Settings INSTANCE = new Settings();

    public int lockTimeoutSeconds = 600;      // 10 minutes
    public int clipboardClearSeconds = 30;

    private Settings() {}

    public static Settings get() { return INSTANCE; }
}
