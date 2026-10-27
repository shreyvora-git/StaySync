package com.staysync.ui.components;

import com.staysync.ui.theme.Fonts;
import com.staysync.ui.theme.Icons;
import com.staysync.ui.theme.Tokens;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;

/**
 * Polished empty-state container with line icon, dual-style heading,
 * explanatory text, and an optional call-to-action button.
 */
public final class EmptyState extends JPanel {
    public EmptyState(String normalTitle, String emphasisTitle, String description, String actionLabel, Runnable onAction) {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(Tokens.space(10), Tokens.space(6), Tokens.space(10), Tokens.space(6)));

        // Icon
        JLabel icon = new JLabel(Icons.get("inbox", 36, Tokens.textMuted()));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Heading with italic emphasis
        EmphasisHeading heading = new EmphasisHeading(normalTitle, emphasisTitle, 20);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Description
        JLabel descLabel = new JLabel(description != null ? description : "");
        descLabel.setFont(Fonts.body());
        descLabel.setForeground(Tokens.textMuted());
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        add(icon);
        add(Box.createVerticalStrut(Tokens.space(4)));
        add(heading);
        add(Box.createVerticalStrut(Tokens.space(2)));
        add(descLabel);

        if (actionLabel != null && onAction != null) {
            add(Box.createVerticalStrut(Tokens.space(5)));
            AppButton actionBtn = new AppButton(actionLabel, AppButton.Variant.PRIMARY, AppButton.Size.MD);
            actionBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            actionBtn.addActionListener(e -> onAction.run());
            add(actionBtn);
        }
    }

    public EmptyState(String normalTitle, String emphasisTitle, String description) {
        this(normalTitle, emphasisTitle, description, null, null);
    }
}
