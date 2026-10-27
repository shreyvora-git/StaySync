package com.staysync.ui.motion;

import com.staysync.config.Prefs;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.function.Consumer;

/**
 * 60 FPS motion and animation coordinator.
 * Honors Prefs.isReduceMotion(): when enabled, animations finish immediately.
 * Always dispatches callbacks on the Swing Event Dispatch Thread (EDT).
 */
public final class Motion {
    public static final int INSTANT_MS = 90;
    public static final int FAST_MS = 160;
    public static final int BASE_MS = 260;
    public static final int SLOW_MS = 480;
    public static final int STAGE_MS = 900;

    public interface Cancellable {
        void cancel();
    }

    public interface Easing {
        float ease(float t);
    }

    // cubic-bezier(0.22, 1, 0.36, 1) approximation
    public static final Easing EASE_OUT = t -> 1.0f - (float) Math.pow(1.0f - t, 3);

    // cubic-bezier(0.65, 0, 0.35, 1) approximation
    public static final Easing EASE_IN_OUT = t -> (t < 0.5f)
            ? 4.0f * t * t * t
            : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3) / 2.0f;

    public static final Easing LINEAR = t -> t;

    private Motion() {}

    public static boolean enabled() {
        return !Prefs.isReduceMotion();
    }

    /**
     * Animate progress from 0.0f to 1.0f over durationMs with easing.
     */
    public static Cancellable animate(int durationMs, Easing easing, Consumer<Float> onFrame, Runnable onComplete) {
        if (!enabled() || durationMs <= 0) {
            SwingUtilities.invokeLater(() -> {
                onFrame.accept(1.0f);
                if (onComplete != null) onComplete.run();
            });
            return () -> {};
        }

        final long startTime = System.nanoTime();
        final long durationNanos = durationMs * 1_000_000L;
        final Easing activeEasing = (easing != null) ? easing : EASE_OUT;

        final Timer[] timerRef = new Timer[1];
        timerRef[0] = new Timer(16, e -> {
            long elapsed = System.nanoTime() - startTime;
            if (elapsed >= durationNanos) {
                timerRef[0].stop();
                onFrame.accept(1.0f);
                if (onComplete != null) onComplete.run();
            } else {
                float rawProgress = (float) elapsed / (float) durationNanos;
                float easedProgress = activeEasing.ease(Math.clamp(rawProgress, 0.0f, 1.0f));
                onFrame.accept(easedProgress);
            }
        });
        timerRef[0].setRepeats(true);
        timerRef[0].start();

        return () -> {
            if (timerRef[0] != null && timerRef[0].isRunning()) {
                timerRef[0].stop();
            }
        };
    }

    public static Cancellable animate(int durationMs, Consumer<Float> onFrame) {
        return animate(durationMs, EASE_OUT, onFrame, null);
    }
}
