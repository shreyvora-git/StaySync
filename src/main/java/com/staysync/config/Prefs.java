package com.staysync.config;

import java.util.prefs.Preferences;

/**
 * Manages user interface preferences stored via java.util.prefs.Preferences.
 * No sensitive account or complaint data is ever persisted here.
 */
public final class Prefs {
    private static final String PREF_NODE = "com.staysync.ui";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_REDUCE_MOTION = "reduce_motion";

    public enum ThemeMode {
        LIGHT,
        DARK,
        SYSTEM;

        public static ThemeMode fromString(String val) {
            if (val == null) return LIGHT;
            try {
                return ThemeMode.valueOf(val.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return LIGHT;
            }
        }
    }

    private static final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    private Prefs() {}

    public static ThemeMode getThemeMode() {
        String val = prefs.get(KEY_THEME, ThemeMode.LIGHT.name());
        return ThemeMode.fromString(val);
    }

    public static void setThemeMode(ThemeMode mode) {
        if (mode != null) {
            prefs.put(KEY_THEME, mode.name());
        }
    }

    public static boolean isReduceMotion() {
        // Allow command line override: -Dstaysync.reduceMotion=true
        String sysProp = System.getProperty("staysync.reduceMotion");
        if (sysProp != null) {
            return Boolean.parseBoolean(sysProp);
        }
        return prefs.getBoolean(KEY_REDUCE_MOTION, false);
    }

    public static void setReduceMotion(boolean reduce) {
        prefs.putBoolean(KEY_REDUCE_MOTION, reduce);
    }
}
