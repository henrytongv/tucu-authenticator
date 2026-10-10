package org.umilab.tucu.auth.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Visual theme for Tucu Authenticator ("Quietud de ribera" design system).
 *
 * Dark mode is the default. Token values live in flatdarklaf.properties /
 * flatlightlaf.properties on the classpath under this class's package; FlatLaf
 * loads them via registerCustomDefaultsSource.
 *
 * Hot-switch: TucuTheme.toggle() / setMode() re-applies the Laf defaults and
 * refreshes the live component tree; custom-painted components read tokens
 * through token() at paint time so they follow automatically.
 */
public final class TucuTheme {

    public enum Mode { DARK, LIGHT }

    private static final String KEY = "tucu.theme.mode";
    private static final Mode DEFAULT = Mode.DARK;

    private static Mode current = DEFAULT;
    private static final List<Runnable> changeListeners = new ArrayList<>();

    private TucuTheme() {}

    /** Must be called before any window is created. */
    public static void install() {
        current = readPersisted();
        // Registers this class's package, so FlatLaf finds
        // com/tucu/auth/ui/flatdarklaf.properties and flatlightlaf.properties.
        FlatLaf.registerCustomDefaultsSource(TucuTheme.class.getPackageName());
        apply();
    }

    public static Mode mode() { return current; }

    public static void toggle() {
        setMode(current == Mode.DARK ? Mode.LIGHT : Mode.DARK);
    }

    /** Components that cache colors or theme-specific artwork can subscribe. */
    public static void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public static void setMode(Mode mode) {
        if (mode == current) return;
        current = mode;
        persist(mode);
        apply();
        // updateUI() re-themes standard Swing components; components that copied
        // a fixed color or swapped artwork listen via addChangeListener.
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
        for (Runnable r : new ArrayList<>(changeListeners)) {
            r.run();
        }
    }

    private static void apply() {
        if (current == Mode.DARK) FlatDarkLaf.setup(); else FlatLightLaf.setup();
        FlatLaf.updateUI();
    }

    private static Mode readPersisted() {
        try {
            return Mode.valueOf(Preferences.userNodeForPackage(TucuTheme.class).get(KEY, DEFAULT.name()));
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }

    private static void persist(Mode mode) {
        Preferences.userNodeForPackage(TucuTheme.class).put(KEY, mode.name());
    }

    // ------------------------------------------------------------------
    // Custom tokens read at paint time by hand-drawn components
    // (tide line, code cell, avatar chips, note boxes).
    // ------------------------------------------------------------------

    public static Color token(String name) {
        Object v = UIManager.get(name);
        if (v instanceof Color c) return c;
        if (v instanceof String s && s.startsWith("#")) return Color.decode(s);
        throw new IllegalArgumentException("Unknown token: " + name);
    }

    /** Tide-line state color for the given seconds remaining in a period. */
    public static Color tideColor(int secondsLeft, int period) {
        double t = period <= 0 ? 0 : (double) secondsLeft / period;
        return token(t > 0.5 ? "Tide.fresh" : t > 0.2 ? "Tide.warm" : "Tide.urgent");
    }
}
