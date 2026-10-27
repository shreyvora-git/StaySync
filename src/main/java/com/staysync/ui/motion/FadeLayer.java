package com.staysync.ui.motion;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.plaf.LayerUI;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * LayerUI helper for animating alpha and subtle vertical slide transitions
 * without mutating component bounds or interfering with layout managers.
 */
public final class FadeLayer extends LayerUI<JComponent> {
    private float alpha = 1.0f;
    private int offsetY = 0;

    public FadeLayer() {}

    public void setAlpha(float alpha) {
        this.alpha = Math.clamp(alpha, 0.0f, 1.0f);
    }

    public float getAlpha() {
        return alpha;
    }

    public void setOffsetY(int offsetY) {
        this.offsetY = offsetY;
    }

    public int getOffsetY() {
        return offsetY;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (alpha < 1.0f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }
            if (offsetY != 0) {
                g2.translate(0, offsetY);
            }
            super.paint(g2, c);
        } finally {
            g2.dispose();
        }
    }

    public static JLayer<JComponent> wrap(JComponent component, FadeLayer layerUI) {
        return new JLayer<>(component, layerUI);
    }
}
