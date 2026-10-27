package com.staysync.ui.components;

import com.staysync.ui.theme.Tokens;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Premium rounded container card featuring multiple presentation variants:
 * DEFAULT (clean surface), STAGE (subtle warm aurora gradient),
 * CONTRAST (deep ink surface with crisp borders), and OUTLINE (transparent surface).
 */
public class Card extends JPanel {
    public enum Variant {
        DEFAULT,
        STAGE,
        CONTRAST,
        OUTLINE,
        TEAL_TINT,
        DEEP_TEAL,
        FLOATING_NAV,
        CREAM_SURFACE
    }

    private final Variant variant;
    private final boolean interactive;
    private boolean isHovered = false;

    public Card(LayoutManager layout, Variant variant, boolean interactive) {
        super(layout);
        this.variant = (variant != null) ? variant : Variant.DEFAULT;
        this.interactive = interactive;
        setOpaque(false);
        int pad = Tokens.space(5); // 20px
        setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));

        if (interactive) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }
    }

    public Card(LayoutManager layout, boolean interactive) {
        this(layout, Variant.DEFAULT, interactive);
    }

    public Card(Variant variant, boolean interactive) {
        this(new BorderLayout(), variant, interactive);
    }

    public Card(Variant variant) {
        this(new BorderLayout(), variant, false);
    }

    public Card(boolean interactive) {
        this(new BorderLayout(), Variant.DEFAULT, interactive);
    }

    public Card() {
        this(new BorderLayout(), Variant.DEFAULT, false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = Tokens.radiusMd();

            switch (variant) {
                case FLOATING_NAV -> {
                    arc = Tokens.radiusMd();
                    g2.setColor(Tokens.surface());
                    g2.fillRoundRect(0, 0, w, h, arc, arc);

                    g2.setColor(Tokens.border());
                    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
                case DEEP_TEAL -> {
                    arc = Tokens.radiusMd();
                    g2.setColor(Tokens.forestGreen());
                    g2.fillRoundRect(0, 0, w, h, arc, arc);

                    g2.setColor(isHovered ? Tokens.forestGreenHover() : Tokens.border());
                    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
                case CREAM_SURFACE -> {
                    arc = Tokens.radiusMd();
                    g2.setColor(isHovered ? Tokens.bgSubtle() : Tokens.surface());
                    g2.fillRoundRect(0, 0, w, h, arc, arc);

                    g2.setColor(isHovered ? Tokens.borderStrong() : Tokens.border());
                    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
                case CONTRAST -> {
                    arc = Tokens.radiusMd();
                    g2.setColor(isHovered ? Tokens.stageSurface() : Tokens.stageBg());
                    g2.fillRoundRect(0, 0, w, h, arc, arc);

                    g2.setColor(Tokens.stageBorder());
                    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
                case STAGE -> {
                    GradientPaint gp = new GradientPaint(
                            0, 0, Tokens.surface(),
                            w, h, Tokens.bgSubtle()
                    );
                    g2.setPaint(gp);
                    g2.fillRoundRect(0, 0, w, h, arc, arc);

                    g2.setColor(isHovered ? Tokens.borderStrong() : Tokens.border());
                    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
                case TEAL_TINT -> {
                    Color bg = isHovered ? Tokens.tealBg() : new Color(Tokens.tealBg().getRed(), Tokens.tealBg().getGreen(), Tokens.tealBg().getBlue(), 160);
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, w, h, arc, arc);

                    g2.setColor(new Color(Tokens.teal().getRed(), Tokens.teal().getGreen(), Tokens.teal().getBlue(), 80));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
                case OUTLINE -> {
                    if (isHovered) {
                        g2.setColor(Tokens.bgSubtle());
                        g2.fillRoundRect(0, 0, w, h, arc, arc);
                    }
                    g2.setColor(isHovered ? Tokens.borderStrong() : Tokens.border());
                    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
                default -> { // DEFAULT
                    g2.setColor(isHovered ? Tokens.surface2() : Tokens.surface());
                    g2.fillRoundRect(0, 0, w, h, arc, arc);

                    g2.setColor(isHovered ? Tokens.borderStrong() : Tokens.border());
                    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
            }

        } finally {
            g2.dispose();
        }
    }
}
