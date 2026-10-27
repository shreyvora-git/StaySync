package com.staysync.ui.theme;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility for loading and tinting SVG icons from src/main/resources/icons/.
 */
public final class Icons {
    private static final Map<String, FlatSVGIcon> cache = new ConcurrentHashMap<>();

    private Icons() {}

    public static FlatSVGIcon get(String name) {
        return get(name, 20, 20);
    }

    public static FlatSVGIcon get(String name, int width, int height) {
        int sw = UIScale.scale(width);
        int sh = UIScale.scale(height);
        String key = name + "_" + sw + "x" + sh;
        return cache.computeIfAbsent(key, k -> new FlatSVGIcon("icons/" + name + ".svg", sw, sh));
    }

    public static FlatSVGIcon get(String name, int size, Color color) {
        FlatSVGIcon icon = get(name, size, size);
        if (color != null) {
            FlatSVGIcon tinted = icon.derive(UIScale.scale(size), UIScale.scale(size));
            tinted.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
            return tinted;
        }
        return icon;
    }
}
