package com.staysync.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.util.SystemInfo;
import com.staysync.config.Prefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;

/**
 * Manages FlatLaf theme initialization, custom defaults source,
 * bundled font registration, and animated theme switching.
 */
public final class ThemeManager {
    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);
    private static boolean initialized = false;

    private ThemeManager() {}

    /**
     * Call before creating any Swing components.
     */
    public static synchronized void init(Prefs.ThemeMode mode) {
        if (initialized) return;

        try {
            FlatInterFont.install();
            FlatJetBrainsMonoFont.install();
            FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
            FlatLaf.registerCustomDefaultsSource("com.staysync.ui.theme");

            applyTheme(mode, false);
            initialized = true;
            log.info("FlatLaf themes and typography initialized with mode: {}", mode);
        } catch (Exception e) {
            log.error("Failed to initialize FlatLaf theme", e);
            // Fallback to basic FlatLightLaf
            FlatLightLaf.setup();
            initialized = true;
        }
    }

    public static void setTheme(Prefs.ThemeMode mode) {
        Prefs.setThemeMode(mode);
        applyTheme(mode, !Prefs.isReduceMotion());
    }

    public static void toggleTheme() {
        if (isDark()) {
            setTheme(Prefs.ThemeMode.LIGHT);
        } else {
            setTheme(Prefs.ThemeMode.DARK);
        }
    }

    public static boolean isDark() {
        return FlatLaf.isLafDark();
    }

    private static void applyTheme(Prefs.ThemeMode mode, boolean animated) {
        boolean darkTarget;
        if (mode == Prefs.ThemeMode.SYSTEM) {
            // Default to light if system detection is ambiguous
            darkTarget = SystemInfo.isWindows && false; // Or default to light for predictable presentation
        } else {
            darkTarget = (mode == Prefs.ThemeMode.DARK);
        }

        Runnable action = () -> {
            if (animated) {
                FlatAnimatedLafChange.showSnapshot();
            }

            if (darkTarget) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }

            FlatLaf.updateUI();

            if (animated) {
                FlatAnimatedLafChange.hideSnapshotWithAnimation();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
