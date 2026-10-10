package org.umilab.tucu.auth.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * UI strings. English is the default (the root bundle); Spanish is selected
 * when the operating system locale is Spanish. An explicit user choice in
 * Settings overrides OS detection and is persisted in Preferences.
 *
 * Bundles live in src/main/resources/i18n/ so translators can maintain them:
 *   messages.properties    (English, fallback)
 *   messages_es.properties (Español)
 */
public final class Messages {

    private static final String BUNDLE = "i18n.messages";
    private static final String KEY = "tucu.lang"; // "", "en", "es"

    private static Locale locale = Locale.ENGLISH;
    private static ResourceBundle bundle;

    private Messages() {}

    /** Call once at startup, before building any UI. */
    public static synchronized void init() {
        String pref = Preferences.userNodeForPackage(Messages.class).get(KEY, "");
        locale = switch (pref) {
            case "es" -> Locale.forLanguageTag("es");
            case "en" -> Locale.ENGLISH;
            default -> detect();
        };
        load();
    }

    /** Spanish OS locales get Spanish; everything else gets English. */
    private static Locale detect() {
        return "es".equalsIgnoreCase(Locale.getDefault().getLanguage())
                ? Locale.forLanguageTag("es")
                : Locale.ENGLISH;
    }

    private static void load() {
        bundle = ResourceBundle.getBundle(BUNDLE, locale);
    }

    public static synchronized Locale locale() { return locale; }

    public static synchronized boolean isSpanish() {
        return "es".equals(locale.getLanguage());
    }

    /** Sets an explicit user language ("en"/"es"), persists it, reloads the bundle. */
    public static synchronized void setLanguage(String tag) {
        locale = "es".equals(tag) ? Locale.forLanguageTag("es") : Locale.ENGLISH;
        Preferences.userNodeForPackage(Messages.class).put(KEY, "es".equals(tag) ? "es" : "en");
        load();
    }

    /** Clears the explicit choice so OS detection applies again. */
    public static synchronized void clearOverride() {
        Preferences.userNodeForPackage(Messages.class).remove(KEY);
        locale = detect();
        load();
    }

    public static synchronized String get(String key) {
        if (bundle == null) init();
        try {
            return bundle.getString(key);
        } catch (java.util.MissingResourceException e) {
            return '!' + key + '!';
        }
    }

    public static String fmt(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }
}
