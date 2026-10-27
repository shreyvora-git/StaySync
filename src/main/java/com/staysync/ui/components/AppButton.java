package com.staysync.ui.components;

import com.formdev.flatlaf.util.UIScale;
import com.staysync.ui.theme.Fonts;
import com.staysync.ui.theme.Tokens;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Modern action button component with Primary, Secondary, Ghost, and Danger variants.
 * Features rounded corners, subtle hover/press states, and an animated spinner loading state.
 */
public class AppButton extends JButton {
    public enum Variant {
        PRIMARY,
        SECONDARY,
        GHOST,
        DANGER,
        TEAL
    }

    public enum Size {
        SM(32, 13),
        MD(40, 14),
        LG(44, 15);

        final int height;
        final int fontSize;

        Size(int height, int fontSize) {
            this.height = height;
            this.fontSize = fontSize;
        }
    }

    private final Variant variant;
    private final Size size;
    private boolean isPill = false;
    private boolean isHovered = false;
    private boolean isPressed = false;
    private boolean isLoading = false;
    private int spinnerAngle = 0;
    private Timer spinnerTimer;

    public AppButton(String text, Variant variant, Size size, boolean isPill) {
        super(text);
        this.variant = (variant != null) ? variant : Variant.PRIMARY;
        this.size = (size != null) ? size : Size.MD;
        this.isPill = isPill;

        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(new Font(com.formdev.flatlaf.fonts.inter.FlatInterFont.FAMILY, Font.BOLD, UIScale.scale(this.size.fontSize)));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled() && !isLoading) {
                    isHovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                isPressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled() && !isLoading) {
                    isPressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }

    public AppButton(String text, Variant variant, Size size) {
        this(text, variant, size, false);
    }

    public AppButton(String text, Variant variant, boolean isPill) {
        this(text, variant, Size.MD, isPill);
    }

    public AppButton(String text, Variant variant) {
        this(text, variant, Size.MD, false);
    }

    public AppButton(String text) {
        this(text, Variant.PRIMARY, Size.MD, false);
    }

    public void setPill(boolean pill) {
        this.isPill = pill;
        repaint();
    }

    public boolean isPill() {
        return isPill;
    }

    public void setLoading(boolean loading) {
        if (this.isLoading == loading) return;
        this.isLoading = loading;
        setEnabled(!loading);

        if (loading) {
            getAccessibleContext().setAccessibleDescription("Loading...");
            spinnerTimer = new Timer(30, e -> {
                spinnerAngle = (spinnerAngle + 20) % 360;
                repaint();
            });
            spinnerTimer.start();
        } else {
            getAccessibleContext().setAccessibleDescription(null);
            if (spinnerTimer != null) {
                spinnerTimer.stop();
                spinnerTimer = null;
            }
        }
        repaint();
    }

    public boolean isLoading() {
        return isLoading;
    }

    @Override
    public Dimension getPreferredSize() {
        int targetH = UIScale.scale(size.height);
        Icon icon = getIcon();
        int iconW = (icon != null) ? icon.getIconWidth() : 0;
        int gap = (icon != null && getText() != null && !getText().isEmpty()) ? UIScale.scale(8) : 0;
        FontMetrics fm = getFontMetrics(getFont());
        String text = getText();
        int textW = (text != null && !text.isEmpty()) ? fm.stringWidth(text) : 0;
        int padH = isPill ? UIScale.scale(32) : UIScale.scale(24);
        int minW = isPill ? UIScale.scale(88) : UIScale.scale(72);
        int targetW = Math.max(iconW + gap + textW + padH, minW);
        return new Dimension(targetW, targetH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = isPill ? h : Tokens.radiusSm();

            // Background Fill
            Color bg = getFillColor();
            if (bg != null) {
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
            }

            // Border for Secondary
            if (variant == Variant.SECONDARY) {
                g2.setColor(isHovered ? Tokens.borderStrong() : Tokens.border());
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }

            // Focus ring
            if (isFocusOwner()) {
                g2.setColor(Tokens.accent());
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            }

            // Spinner during loading
            if (isLoading) {
                int spinnerSize = UIScale.scale(16);
                int sx = (w - spinnerSize) / 2;
                int sy = (h - spinnerSize) / 2;
                g2.setColor(getFgColor());
                g2.drawArc(sx, sy, spinnerSize, spinnerSize, spinnerAngle, 260);
                return;
            }

            // Foreground Icon & Text
            Icon icon = getIcon();
            String text = getText();
            FontMetrics fm = g2.getFontMetrics(getFont());
            int iconW = (icon != null) ? icon.getIconWidth() : 0;
            int iconH = (icon != null) ? icon.getIconHeight() : 0;
            int textW = (text != null && !text.isEmpty()) ? fm.stringWidth(text) : 0;
            int gap = (icon != null && text != null && !text.isEmpty()) ? UIScale.scale(6) : 0;
            int totalContentW = iconW + gap + textW;
            int startX = (w - totalContentW) / 2;

            if (icon != null) {
                int iconY = (h - iconH) / 2;
                icon.paintIcon(this, g2, startX, iconY);
                startX += iconW + gap;
            }

            if (text != null && !text.isEmpty()) {
                g2.setFont(getFont());
                g2.setColor(isEnabled() ? getFgColor() : Tokens.textFaint());
                int y = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(text, startX, y);
            }
        } finally {
            g2.dispose();
        }
    }

    private Color getFillColor() {
        if (!isEnabled()) {
            return (variant == Variant.GHOST) ? null : Tokens.bgSubtle();
        }
        return switch (variant) {
            case PRIMARY -> isPressed ? Tokens.accentHover() : (isHovered ? Tokens.accentHover() : Tokens.accent());
            case SECONDARY -> isHovered ? Tokens.bgSubtle() : Tokens.surface();
            case GHOST -> isHovered ? Tokens.bgSubtle() : null;
            case DANGER -> isPressed ? Tokens.danger().darker() : (isHovered ? Tokens.danger().brighter() : Tokens.danger());
            case TEAL -> isPressed ? Tokens.deepTealBorder() : (isHovered ? Tokens.deepTealSurface() : Tokens.teal());
        };
    }

    private Color getFgColor() {
        return switch (variant) {
            case PRIMARY, DANGER, TEAL -> Tokens.accentOn();
            case SECONDARY, GHOST -> Tokens.text();
        };
    }
}
