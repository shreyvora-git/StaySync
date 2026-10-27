package com.staysync.ui.motion;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Screen transition host. Swaps active view components with an elegant
 * snapshot cross-fade transition over Motion.BASE_MS (260 ms).
 */
public final class ScreenHost extends JPanel {
    private JComponent currentScreen;
    private BufferedImage transitionSnapshot;
    private float snapshotAlpha = 0.0f;
    private Motion.Cancellable activeTransition;

    public ScreenHost() {
        super(new BorderLayout());
        setOpaque(false);
    }

    public void showScreen(JComponent newScreen) {
        if (newScreen == currentScreen) return;

        if (activeTransition != null) {
            activeTransition.cancel();
            activeTransition = null;
        }

        if (!Motion.enabled() || currentScreen == null || getWidth() <= 0 || getHeight() <= 0) {
            transitionSnapshot = null;
            snapshotAlpha = 0.0f;
            removeAll();
            currentScreen = newScreen;
            if (newScreen != null) {
                add(newScreen, BorderLayout.CENTER);
            }
            revalidate();
            repaint();
            return;
        }

        // Capture snapshot of previous screen
        BufferedImage snapshot = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = snapshot.createGraphics();
        currentScreen.paint(g2);
        g2.dispose();

        this.transitionSnapshot = snapshot;
        this.snapshotAlpha = 1.0f;

        removeAll();
        currentScreen = newScreen;
        if (newScreen != null) {
            add(newScreen, BorderLayout.CENTER);
        }
        revalidate();

        activeTransition = Motion.animate(Motion.BASE_MS, Motion.EASE_OUT, progress -> {
            snapshotAlpha = 1.0f - progress;
            repaint();
        }, () -> {
            transitionSnapshot = null;
            snapshotAlpha = 0.0f;
            repaint();
        });
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (transitionSnapshot != null && snapshotAlpha > 0.01f) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, snapshotAlpha));
                g2.drawImage(transitionSnapshot, 0, 0, null);
            } finally {
                g2.dispose();
            }
        }
    }
}
