package com.staysync.ui.components;

import com.staysync.ui.theme.Fonts;
import com.staysync.ui.theme.Tokens;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Editorial heading component that blends clean Sans-Serif with
 * contrasting Serif Italic emphasis (e.g. "Don't just complain. *Get it fixed.*").
 * Supports stacked, inline, and centered typographic alignments.
 */
public final class EmphasisHeading extends JComponent {
    private String normalText;
    private String emphasisText;
    private float fontSize;
    private boolean stacked;
    private boolean centered;
    private Color textColor;
    private Color emphasisColor;

    public EmphasisHeading(String normalText, String emphasisText, float fontSize, boolean stacked, boolean centered) {
        this.normalText = (normalText != null) ? normalText : "";
        this.emphasisText = (emphasisText != null) ? emphasisText : "";
        this.fontSize = fontSize;
        this.stacked = stacked;
        this.centered = centered;
        setOpaque(false);
        updatePreferredSize();
    }

    public EmphasisHeading(String normalText, String emphasisText, float fontSize, boolean stacked) {
        this(normalText, emphasisText, fontSize, stacked, false);
    }

    public EmphasisHeading(String normalText, String emphasisText, float fontSize) {
        this(normalText, emphasisText, fontSize, false, false);
    }

    public void setText(String normal, String emphasis) {
        this.normalText = (normal != null) ? normal : "";
        this.emphasisText = (emphasis != null) ? emphasis : "";
        updatePreferredSize();
        repaint();
    }

    public void setStacked(boolean stacked) {
        this.stacked = stacked;
        updatePreferredSize();
        repaint();
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
        repaint();
    }

    public void setTextColor(Color color) {
        this.textColor = color;
        repaint();
    }

    public void setEmphasisColor(Color color) {
        this.emphasisColor = color;
        repaint();
    }

    private void updatePreferredSize() {
        Font fNormal = Fonts.heading(Math.round(fontSize));
        Font fEmph = Fonts.serifItalic(stacked ? fontSize * 1.08f : fontSize);

        FontMetrics fmNormal = getFontMetrics(fNormal);
        FontMetrics fmEmph = getFontMetrics(fEmph);

        if (stacked) {
            int width = Math.max(fmNormal.stringWidth(normalText), fmEmph.stringWidth(emphasisText)) + 32;
            int height = fmNormal.getHeight() + fmEmph.getHeight() + 18;
            setPreferredSize(new Dimension(width, height));
            setMinimumSize(new Dimension(Math.min(width, 280), height));
        } else {
            int spaceW = (!normalText.isEmpty() && !normalText.endsWith(" ") && !emphasisText.isEmpty()) ? fmNormal.charWidth(' ') : 0;
            int width = fmNormal.stringWidth(normalText) + spaceW + fmEmph.stringWidth(emphasisText) + 32;
            int height = Math.max(fmNormal.getHeight(), fmEmph.getHeight()) + 14;
            setPreferredSize(new Dimension(width, height));
            setMinimumSize(new Dimension(Math.min(width, 280), height));
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        updatePreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            Font fNormal = Fonts.heading(Math.round(fontSize));
            Font fEmph = Fonts.serifItalic(stacked ? fontSize * 1.08f : fontSize);

            FontMetrics fmNormal = g2.getFontMetrics(fNormal);
            FontMetrics fmEmph = g2.getFontMetrics(fEmph);

            Color normColor = (textColor != null) ? textColor : Tokens.text();
            Color emphColor = (emphasisColor != null) ? emphasisColor : Tokens.accent();

            int w = getWidth();

            if (stacked) {
                // Line 1: Normal Sans Heading
                int y1 = fmNormal.getAscent() + 4;
                int x1 = centered ? Math.max(0, (w - fmNormal.stringWidth(normalText)) / 2) : 0;
                if (!normalText.isEmpty()) {
                    g2.setFont(fNormal);
                    g2.setColor(normColor);
                    g2.drawString(normalText, x1, y1);
                }

                // Line 2: Serif Italic Emphasis
                int y2 = y1 + fmNormal.getDescent() + fmEmph.getAscent() + 8;
                int x2 = centered ? Math.max(0, (w - fmEmph.stringWidth(emphasisText)) / 2) : 0;
                if (!emphasisText.isEmpty()) {
                    g2.setFont(fEmph);
                    g2.setColor(emphColor);
                    g2.drawString(emphasisText, x2, y2);
                }
            } else {
                int baseline = Math.max(fmNormal.getAscent(), fmEmph.getAscent()) + 2;
                int spaceW = (!normalText.isEmpty() && !normalText.endsWith(" ") && !emphasisText.isEmpty()) ? fmNormal.charWidth(' ') : 0;
                int totalW = fmNormal.stringWidth(normalText) + spaceW + fmEmph.stringWidth(emphasisText);
                int x = centered ? Math.max(0, (w - totalW) / 2) : 0;

                // Draw normal text
                if (!normalText.isEmpty()) {
                    g2.setFont(fNormal);
                    g2.setColor(normColor);
                    g2.drawString(normalText, x, baseline);
                    x += fmNormal.stringWidth(normalText) + spaceW;
                }

                // Draw italic emphasis text
                if (!emphasisText.isEmpty()) {
                    g2.setFont(fEmph);
                    g2.setColor(emphColor);
                    g2.drawString(emphasisText, x, baseline);
                }
            }
        } finally {
            g2.dispose();
        }
    }
}
