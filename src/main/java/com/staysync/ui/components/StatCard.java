package com.staysync.ui.components;

import com.staysync.ui.motion.Motion;
import com.staysync.ui.theme.Fonts;
import com.staysync.ui.theme.Tokens;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import java.awt.Color;

/**
 * KPI StatCard displaying an eyebrow title, big JetBrains Mono numeric value
 * with animated count-up on initial show, and a descriptive subtitle.
 */
public final class StatCard extends Card {
    private final JLabel eyebrowLabel;
    private final JLabel valueLabel;
    private final JLabel subLabel;
    private int targetValue;
    private int displayedValue;
    private Motion.Cancellable countUpAnimation;

    public StatCard(String eyebrow, int initialValue, String subtitle, Color accentColor) {
        super(false);
        this.targetValue = initialValue;
        this.displayedValue = initialValue;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Eyebrow
        eyebrowLabel = new JLabel(eyebrow.toUpperCase());
        eyebrowLabel.setFont(Fonts.xs());
        eyebrowLabel.setForeground(Tokens.textMuted());

        // Value in Tabular JetBrains Mono
        valueLabel = new JLabel(String.valueOf(initialValue));
        valueLabel.setFont(Fonts.monoBold(28));
        valueLabel.setForeground(accentColor != null ? accentColor : Tokens.text());

        // Subtitle
        subLabel = new JLabel(subtitle != null ? subtitle : "");
        subLabel.setFont(Fonts.sm());
        subLabel.setForeground(Tokens.textFaint());

        add(eyebrowLabel);
        add(Box.createVerticalStrut(Tokens.space(2)));
        add(valueLabel);
        add(Box.createVerticalStrut(Tokens.space(1)));
        add(subLabel);
    }

    public StatCard(String eyebrow, int initialValue, String subtitle) {
        this(eyebrow, initialValue, subtitle, null);
    }

    public void setValue(int newValue, boolean animate) {
        int oldValue = this.targetValue;
        this.targetValue = newValue;

        if (countUpAnimation != null) {
            countUpAnimation.cancel();
            countUpAnimation = null;
        }

        if (!animate || !Motion.enabled()) {
            this.displayedValue = newValue;
            valueLabel.setText(String.valueOf(newValue));
            return;
        }

        countUpAnimation = Motion.animate(Motion.SLOW_MS, Motion.EASE_OUT, progress -> {
            int current = Math.round(oldValue + (newValue - oldValue) * progress);
            displayedValue = current;
            valueLabel.setText(String.valueOf(current));
        }, () -> {
            displayedValue = newValue;
            valueLabel.setText(String.valueOf(newValue));
        });
    }

    public void setValue(int newValue) {
        setValue(newValue, true);
    }

    public int getValue() {
        return targetValue;
    }
}
