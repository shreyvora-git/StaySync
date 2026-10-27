package com.staysync.ui.components;

import com.staysync.config.Prefs;
import com.staysync.ui.theme.Tokens;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;

/**
 * Shimmering loading skeleton panel that mirrors final screen layouts.
 * Automatically stops its shimmer timer when removed from the component hierarchy.
 */
public class SkeletonPanel extends JComponent {
    private final int rows;
    private final int rowHeight;
    private final int rowArc;
    private float shimmerProgress = 0.0f;
    private Timer shimmerTimer;

    public SkeletonPanel(int rows, int rowHeight, int rowArc) {
        this.rows = Math.max(1, rows);
        this.rowHeight = rowHeight;
        this.rowArc = rowArc;
        setOpaque(false);

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    startShimmer();
                } else {
                    stopShimmer();
                }
            }
        });
    }

    public SkeletonPanel(int rows) {
        this(rows, 48, 12);
    }

    public SkeletonPanel() {
        this(5, 48, 12);
    }

    private void startShimmer() {
        if (shimmerTimer != null && shimmerTimer.isRunning()) return;
        if (Prefs.isReduceMotion()) return;

        shimmerTimer = new Timer(33, e -> {
            shimmerProgress = (shimmerProgress + 0.025f) % 1.0f;
            repaint();
        });
        shimmerTimer.start();
    }

    private void stopShimmer() {
        if (shimmerTimer != null) {
            shimmerTimer.stop();
            shimmerTimer = null;
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        stopShimmer();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            Color baseColor = Tokens.bgSubtle();
            Color highlightColor = Tokens.surface();

            int spacing = Tokens.space(3);
            int currentY = 0;

            for (int i = 0; i < rows; i++) {
                if (currentY + rowHeight > h && i > 0) break;

                // Moving gradient highlight
                if (!Prefs.isReduceMotion()) {
                    float startX = (w * shimmerProgress) - (w / 2.0f);
                    GradientPaint gp = new GradientPaint(
                            startX, 0, baseColor,
                            startX + (w / 3.0f), 0, highlightColor,
                            true
                    );
                    g2.setPaint(gp);
                } else {
                    g2.setColor(baseColor);
                }

                g2.fillRoundRect(0, currentY, w, rowHeight, rowArc, rowArc);
                currentY += rowHeight + spacing;
            }
        } finally {
            g2.dispose();
        }
    }
}
