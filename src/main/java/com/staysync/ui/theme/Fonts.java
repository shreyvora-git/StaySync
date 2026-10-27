package com.staysync.ui.theme;

import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.util.UIScale;

import java.awt.Font;
import java.io.InputStream;

/**
 * Typography system for StaySync.
 * Uses Inter for UI and Headings, JetBrains Mono for IDs and numbers,
 * and Instrument Serif (or Georgia) Italic for editorial emphasis words.
 */
public final class Fonts {
    private static Font instrumentSerifItalic;

    static {
        try (InputStream is = Fonts.class.getResourceAsStream("/fonts/InstrumentSerif-Italic.ttf")) {
            if (is != null) {
                instrumentSerifItalic = Font.createFont(Font.TRUETYPE_FONT, is);
            }
        } catch (Exception ignored) {
            // Fallback to Georgia italic below
        }
    }

    private Fonts() {}

    public static Font heroOversized() {
        return heading(32);
    }

    public static Font hero() {
        return heading(28);
    }

    public static Font displayLarge() {
        return heading(26);
    }

    public static Font display() {
        return heading(24);
    }

    public static Font h1() {
        return heading(22);
    }

    public static Font bodyLarge() {
        return new Font(FlatInterFont.FAMILY, Font.PLAIN, UIScale.scale(16));
    }

    public static Font bodyLargeMedium() {
        return new Font(FlatInterFont.FAMILY, Font.BOLD, UIScale.scale(16));
    }

    public static Font h2() {
        return heading(18);
    }

    public static Font h3() {
        return heading(16);
    }

    public static Font body() {
        return new Font(FlatInterFont.FAMILY, Font.PLAIN, UIScale.scale(14));
    }

    public static Font bodyBold() {
        return new Font(FlatInterFont.FAMILY, Font.BOLD, UIScale.scale(14));
    }

    public static Font sm() {
        return new Font(FlatInterFont.FAMILY, Font.PLAIN, UIScale.scale(13));
    }

    public static Font smMedium() {
        return new Font(FlatInterFont.FAMILY, Font.BOLD, UIScale.scale(13));
    }

    public static Font xs() {
        return new Font(FlatInterFont.FAMILY, Font.BOLD, UIScale.scale(12));
    }

    public static Font mono(float size) {
        return new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, UIScale.scale(Math.round(size)));
    }

    public static Font monoBold(float size) {
        return new Font(FlatJetBrainsMonoFont.FAMILY, Font.BOLD, UIScale.scale(Math.round(size)));
    }

    public static Font heading(int size) {
        return new Font(FlatInterFont.FAMILY, Font.BOLD, UIScale.scale(size));
    }

    public static Font serifItalic(float size) {
        int scaled = UIScale.scale(Math.round(size));
        if (instrumentSerifItalic != null) {
            return instrumentSerifItalic.deriveFont(Font.ITALIC, (float) scaled);
        }
        return new Font("Georgia", Font.ITALIC, scaled);
    }
}
