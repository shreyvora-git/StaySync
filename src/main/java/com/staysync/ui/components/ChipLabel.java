package com.staysync.ui.components;

import com.formdev.flatlaf.util.UIScale;
import com.staysync.model.Urgency;
import com.staysync.ui.theme.Fonts;
import com.staysync.ui.theme.Icons;
import com.staysync.ui.theme.Tokens;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Modern pill badge / chip component for Status, Priority, Confidentiality, and Categories.
 * Strictly adheres to accessibility rules: NEVER relies on color alone.
 */
public final class ChipLabel extends JComponent {
    public enum Type {
        STATUS,
        PRIORITY,
        CONFIDENTIAL,
        CATEGORY
    }

    private final Type type;
    private final String text;
    private final Color fg;
    private final Color bg;
    private final String iconType; // "dot", "pulse_dot", "check", "bars_1", "bars_2", "bars_3", "shield", "none"

    private ChipLabel(Type type, String text, Color fg, Color bg, String iconType) {
        this.type = type;
        this.text = text;
        this.fg = fg;
        this.bg = bg;
        this.iconType = iconType;
        setOpaque(false);
        setFont(Fonts.xs());
    }

    public static ChipLabel status(String status) {
        if (status == null) status = "Pending";
        return switch (status.trim().toLowerCase()) {
            case "in progress", "in_progress" ->
                    new ChipLabel(Type.STATUS, "In Progress", Tokens.forestGreen(), Tokens.softSage(), "sync_icon");
            case "resolved" ->
                    new ChipLabel(Type.STATUS, "Resolved", Tokens.forestGreen(), Tokens.softSage(), "check");
            default ->
                    new ChipLabel(Type.STATUS, "Pending", new Color(0x92, 0x40, 0x0E), new Color(0xFE, 0xF3, 0xC7), "clock");
        };
    }

    public static ChipLabel priority(String priority) {
        if (priority == null || priority.isBlank()) {
            return new ChipLabel(Type.PRIORITY, "Normal", Tokens.prioNoneFg(), Tokens.prioNoneBg(), "dashed");
        }
        return switch (priority.trim().toLowerCase()) {
            case "low" ->
                    new ChipLabel(Type.PRIORITY, "Low", new Color(0x15, 0x80, 0x3D), new Color(0xDC, 0xFC, 0xE7), "dot");
            case "high" ->
                    new ChipLabel(Type.PRIORITY, "High", new Color(0xB9, 0x1C, 0x1C), new Color(0xFE, 0xE2, 0xE2), "dot");
            default ->
                    new ChipLabel(Type.PRIORITY, "Medium", new Color(0xB4, 0x53, 0x09), new Color(0xFE, 0xF3, 0xC7), "dot");
        };
    }

    public static ChipLabel urgency(Urgency urgency) {
        if (urgency == null) {
            return new ChipLabel(Type.PRIORITY, "Medium Urgency", new Color(0xB4, 0x53, 0x09), new Color(0xFE, 0xF3, 0xC7), "dot");
        }
        return switch (urgency) {
            case HIGH ->
                    new ChipLabel(Type.PRIORITY, "High Urgency", new Color(0xB9, 0x1C, 0x1C), new Color(0xFE, 0xE2, 0xE2), "dot");
            case LOW ->
                    new ChipLabel(Type.PRIORITY, "Low Urgency", new Color(0x15, 0x80, 0x3D), new Color(0xDC, 0xFC, 0xE7), "dot");
            case MEDIUM ->
                    new ChipLabel(Type.PRIORITY, "Medium Urgency", new Color(0xB4, 0x53, 0x09), new Color(0xFE, 0xF3, 0xC7), "dot");
        };
    }

    public static ChipLabel confidential() {
        return new ChipLabel(Type.CONFIDENTIAL, "Private", Tokens.confFg(), Tokens.confBg(), "shield");
    }

    public static ChipLabel category(String name) {
        return new ChipLabel(Type.CATEGORY, name != null ? name : "General", Tokens.textMuted(), Tokens.bgSubtle(), "none");
    }

    @Override
    public Dimension getPreferredSize() {
        Font f = getFont();
        FontMetrics fm = getFontMetrics(f);
        int textW = fm.stringWidth(text);
        int iconW = "none".equals(iconType) ? 0 : UIScale.scale(14);
        int padH = UIScale.scale(16);
        int w = textW + iconW + padH;
        int h = UIScale.scale(24);
        return new Dimension(w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = h; // Full pill shape

            // Fill background
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            // Optional dashed border for unassigned
            if ("dashed".equals(iconType)) {
                g2.setColor(fg);
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{3.0f, 3.0f}, 0.0f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }

            int x = UIScale.scale(8);

            // Draw indicator icon
            switch (iconType) {
                case "dot", "pulse_dot" -> {
                    int dotSize = UIScale.scale(6);
                    int dotY = (h - dotSize) / 2;
                    g2.setColor(fg);
                    g2.fillOval(x, dotY, dotSize, dotSize);
                    x += dotSize + UIScale.scale(5);
                }
                case "check" -> {
                    g2.setColor(fg);
                    g2.setStroke(new BasicStroke(UIScale.scale(1.8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cy = h / 2;
                    g2.drawLine(x, cy, x + UIScale.scale(3), cy + UIScale.scale(3));
                    g2.drawLine(x + UIScale.scale(3), cy + UIScale.scale(3), x + UIScale.scale(8), cy - UIScale.scale(3));
                    x += UIScale.scale(11);
                }
                case "clock" -> {
                    int size = UIScale.scale(9);
                    int cy = (h - size) / 2;
                    g2.setColor(fg);
                    g2.drawOval(x, cy, size, size);
                    g2.drawLine(x + size / 2, cy + 2, x + size / 2, cy + size / 2);
                    g2.drawLine(x + size / 2, cy + size / 2, x + size - 2, cy + size / 2);
                    x += size + UIScale.scale(4);
                }
                case "sync_icon" -> {
                    int size = UIScale.scale(9);
                    int cy = (h - size) / 2;
                    g2.setColor(fg);
                    g2.setStroke(new BasicStroke(UIScale.scale(1.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawArc(x, cy, size, size, 45, 270);
                    g2.drawLine(x + size - 2, cy + 1, x + size, cy + 3);
                    x += size + UIScale.scale(4);
                }
                case "shield" -> {
                    int iconSize = UIScale.scale(12);
                    int iconY = (h - iconSize) / 2;
                    Icons.get("shield", iconSize, fg).paintIcon(this, g2, x, iconY);
                    x += iconSize + UIScale.scale(4);
                }
                case "bars_1", "bars_2", "bars_3" -> {
                    g2.setColor(fg);
                    int numBars = "bars_1".equals(iconType) ? 1 : ("bars_2".equals(iconType) ? 2 : 3);
                    int barW = UIScale.scale(2);
                    int barMaxH = UIScale.scale(9);
                    for (int i = 0; i < 3; i++) {
                        int bh = UIScale.scale(3 + i * 3);
                        int by = (h + barMaxH) / 2 - bh;
                        if (i < numBars) {
                            g2.fillRect(x + i * UIScale.scale(3), by, barW, bh);
                        } else {
                            g2.setColor(Tokens.borderStrong());
                            g2.fillRect(x + i * UIScale.scale(3), by, barW, bh);
                            g2.setColor(fg);
                        }
                    }
                    x += UIScale.scale(12);
                }
            }

            // Draw label text
            g2.setFont(getFont());
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            int baseline = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, x, baseline);

        } finally {
            g2.dispose();
        }
    }
}
