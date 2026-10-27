package com.staysync.ui.theme;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * Single source of truth for design tokens.
 * All colors, radii, and spacing are retrieved dynamically from UIManager
 * to ensure seamless live theme switching without hard-coded constants.
 */
public final class Tokens {
    private Tokens() {}

    private static Color color(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return (c != null) ? c : fallback;
    }

    private static int intVal(String key, int fallback) {
        Object val = UIManager.get(key);
        if (val instanceof Integer i) return i;
        return fallback;
    }

    // Surfaces and Backgrounds
    public static Color bg() { return color("Panel.background", new Color(0xFDFBD4)); }
    public static Color bgSubtle() { return color("StaySync.bg.subtle", new Color(0xF7F5C8)); }
    public static Color surface() { return color("StaySync.surface", new Color(0xFFFEF0)); }
    public static Color surface2() { return color("StaySync.surface2", new Color(0xFBF9E4)); }
    public static Color scrim() { return color("StaySync.scrim", new Color(0x29, 0x2B, 0x26, 0x73)); }

    // Borders
    public static Color border() { return color("StaySync.border", new Color(0xDED6C8)); }
    public static Color borderStrong() { return color("StaySync.border.strong", new Color(0xC8C0B2)); }

    // Typography
    public static Color text() { return color("StaySync.text", new Color(0x292B26)); }
    public static Color textMuted() { return color("StaySync.text.muted", new Color(0x66685F)); }
    public static Color textFaint() { return color("StaySync.text.faint", new Color(0x8F9187)); }

    // Brand Accent & Forest Green
    public static Color accent() { return color("StaySync.accent", new Color(0x064E3B)); }
    public static Color accentHover() { return color("StaySync.accent.hover", new Color(0x043D2E)); }
    public static Color accentSoft() { return color("StaySync.accent.soft", new Color(0xDCE9DF)); }
    public static Color accentOn() { return color("StaySync.accent.on", Color.WHITE); }

    public static Color forestGreen() { return color("StaySync.forestGreen", new Color(0x064E3B)); }
    public static Color forestGreenHover() { return color("StaySync.forestGreen.hover", new Color(0x043D2E)); }
    public static Color softSage() { return color("StaySync.softSage", new Color(0xDCE9DF)); }

    // Status Colors
    public static Color statusPendingFg() { return color("StaySync.status.pending.fg", new Color(0x8A5200)); }
    public static Color statusPendingBg() { return color("StaySync.status.pending.bg", new Color(0xFFF1D6)); }
    public static Color statusProgressFg() { return color("StaySync.status.progress.fg", new Color(0x1D4ED8)); }
    public static Color statusProgressBg() { return color("StaySync.status.progress.bg", new Color(0xE3EDFF)); }
    public static Color statusResolvedFg() { return color("StaySync.status.resolved.fg", new Color(0x064E3B)); }
    public static Color statusResolvedBg() { return color("StaySync.status.resolved.bg", new Color(0xDCE9DF)); }

    // Priority Colors
    public static Color prioLowFg() { return color("StaySync.prio.low.fg", new Color(0x475569)); }
    public static Color prioLowBg() { return color("StaySync.prio.low.bg", new Color(0xECEFF3)); }
    public static Color prioMedFg() { return color("StaySync.prio.med.fg", new Color(0x9A3412)); }
    public static Color prioMedBg() { return color("StaySync.prio.med.bg", new Color(0xFFE8D9)); }
    public static Color prioHighFg() { return color("StaySync.prio.high.fg", new Color(0xB42318)); }
    public static Color prioHighBg() { return color("StaySync.prio.high.bg", new Color(0xFFE3E0)); }
    public static Color prioNoneFg() { return color("StaySync.prio.none.fg", new Color(0x6B6860)); }
    public static Color prioNoneBg() { return color("StaySync.prio.none.bg", new Color(0xF1EEE8)); }

    // Confidential Shield Colors
    public static Color confFg() { return color("StaySync.conf.fg", new Color(0x6B2D7B)); }
    public static Color confBg() { return color("StaySync.conf.bg", new Color(0xF5E6FA)); }

    // Semantic Feedback
    public static Color danger() { return color("StaySync.danger", new Color(0xC0362C)); }
    public static Color success() { return color("StaySync.success", new Color(0x064E3B)); }
    public static Color warning() { return color("StaySync.warning", new Color(0xB45309)); }
    public static Color info() { return color("StaySync.info", new Color(0x1D4ED8)); }

    // Aurora Colors (Welcome / Empty stages)
    public static Color aurora1() { return color("StaySync.aurora.1", new Color(0xFFD9C2)); }
    public static Color aurora2() { return color("StaySync.aurora.2", new Color(0xD9D6FF)); }
    public static Color aurora3() { return color("StaySync.aurora.3", new Color(0xCFE9FF)); }

    // Stage & Accent Containers
    public static Color stageBg() { return color("StaySync.stage.bg", new Color(0x043D2E)); }
    public static Color stageSurface() { return color("StaySync.stage.surface", new Color(0x064E3B)); }
    public static Color stageText() { return color("StaySync.stage.text", new Color(0xFFFEF0)); }
    public static Color stageSubtext() { return color("StaySync.stage.subtext", new Color(0xDCE9DF)); }
    public static Color stageBorder() { return color("StaySync.stage.border", new Color(0x0A5C47)); }

    // Complementary Teal / Forest Green
    public static Color teal() { return color("StaySync.teal", new Color(0x064E3B)); }
    public static Color tealBg() { return color("StaySync.teal.bg", new Color(0xDCE9DF)); }
    public static Color tealFg() { return color("StaySync.teal.fg", new Color(0x064E3B)); }
    public static Color tealAccent() { return new Color(0x34D399); }

    // Palette: Warm Cream, Surface, Borders
    public static Color canvasCream() { return color("StaySync.canvas.cream", new Color(0xFDFBD4)); }
    public static Color creamSurface() { return color("StaySync.cream.surface", new Color(0xFFFEF0)); }
    public static Color creamBorder() { return color("StaySync.cream.border", new Color(0xDED6C8)); }

    public static Color deepTealBg() { return color("StaySync.deepTeal.bg", new Color(0x064E3B)); }
    public static Color deepTealSurface() { return color("StaySync.deepTeal.surface", new Color(0x043D2E)); }
    public static Color deepTealText() { return color("StaySync.deepTeal.text", new Color(0xFFFEF0)); }
    public static Color deepTealSubtext() { return color("StaySync.deepTeal.subtext", new Color(0xDCE9DF)); }
    public static Color deepTealBorder() { return color("StaySync.deepTeal.border", new Color(0x0A5C47)); }

    public static Color floatingNavBg() { return color("StaySync.floatingNav.bg", new Color(0xFF, 0xFE, 0xF0, 0xFA)); }
    public static Color floatingNavBorder() { return color("StaySync.floatingNav.border", new Color(0xDE, 0xD6, 0xC8)); }

    // Okabe-Ito Chart Colors (Index 1 to 5)
    public static Color chartColor(int index) {
        String key = "StaySync.chart." + Math.clamp(index, 1, 5);
        return color(key, new Color(0x0072B2));
    }

    // Radii
    public static int radiusSm() { return UIScale.scale(intVal("StaySync.radius.sm", 8)); }
    public static int radiusMd() { return UIScale.scale(intVal("StaySync.radius.md", 12)); }
    public static int radiusLg() { return UIScale.scale(intVal("StaySync.radius.lg", 20)); }
    public static int radiusXl() { return UIScale.scale(intVal("StaySync.radius.xl", 28)); }

    // Spacing (Base unit 4px)
    public static int space(int units) {
        return UIScale.scale(units * 4);
    }
}
