package com.staysync.ui.components;

import com.staysync.ui.theme.Fonts;
import com.staysync.ui.theme.Icons;
import com.staysync.ui.theme.Tokens;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Modern alert and notice banner component.
 * Supports NOTICE (calm informational style for design and environment notices)
 * and DANGER (actionable error feedback).
 */
public final class ErrorBanner extends JPanel {
    public enum Type {
        NOTICE,
        INFO,
        DANGER
    }

    private final Type type;
    private final JLabel messageLabel;
    private final AppButton actionButton;

    public ErrorBanner(String message, Type type, String actionText, Runnable onAction) {
        super(new BorderLayout(Tokens.space(3), 0));
        this.type = (type != null) ? type : Type.DANGER;
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(Tokens.space(3), Tokens.space(4), Tokens.space(3), Tokens.space(4)));

        // Icon + Message left box
        JPanel leftBox = new JPanel();
        leftBox.setOpaque(false);
        leftBox.setLayout(new BoxLayout(leftBox, BoxLayout.X_AXIS));

        String iconName = switch (this.type) {
            case NOTICE -> "shield";
            case INFO -> "check";
            case DANGER -> "alert-circle";
        };
        Color iconColor = switch (this.type) {
            case NOTICE -> Tokens.accent();
            case INFO -> Tokens.teal();
            case DANGER -> Tokens.danger();
        };

        JLabel iconLabel = new JLabel(Icons.get(iconName, 18, iconColor));
        messageLabel = new JLabel(message != null ? message : "");
        messageLabel.setFont(Fonts.sm());
        messageLabel.setForeground(Tokens.text());

        leftBox.add(iconLabel);
        leftBox.add(Box.createHorizontalStrut(Tokens.space(3)));
        leftBox.add(messageLabel);

        add(leftBox, BorderLayout.CENTER);

        if (onAction != null && actionText != null) {
            actionButton = new AppButton(actionText, AppButton.Variant.GHOST, AppButton.Size.SM);
            actionButton.addActionListener(e -> onAction.run());
            add(actionButton, BorderLayout.EAST);
        } else {
            actionButton = null;
        }
    }

    public ErrorBanner(String message, Runnable onRetry) {
        this(message, Type.DANGER, "Try again", onRetry);
    }

    public ErrorBanner(String message) {
        this(message, Type.DANGER, null, null);
    }

    public static ErrorBanner notice(String message) {
        return new ErrorBanner(message, Type.NOTICE, null, null);
    }

    public static ErrorBanner notice(String message, String actionText, Runnable onAction) {
        return new ErrorBanner(message, Type.NOTICE, actionText, onAction);
    }

    public void setMessage(String message) {
        messageLabel.setText(message != null ? message : "");
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

            Color base = switch (type) {
                case NOTICE -> Tokens.accent();
                case INFO -> Tokens.teal();
                case DANGER -> Tokens.danger();
            };

            // Soft translucent background
            Color bg = new Color(base.getRed(), base.getGreen(), base.getBlue(), (type == Type.NOTICE) ? 14 : 22);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            // Subtle 1px border
            Color border = new Color(base.getRed(), base.getGreen(), base.getBlue(), (type == Type.NOTICE) ? 50 : 80);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

        } finally {
            g2.dispose();
        }
    }
}
