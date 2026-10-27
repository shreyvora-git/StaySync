package com.staysync.ui;

import com.formdev.flatlaf.util.UIScale;
import com.staysync.error.AuthenticationException;
import com.staysync.error.ValidationException;
import com.staysync.security.CurrentUser;
import com.staysync.service.AuthResult;
import com.staysync.service.AuthService;
import com.staysync.ui.components.AppButton;
import com.staysync.ui.components.Card;
import com.staysync.ui.components.ErrorBanner;
import com.staysync.ui.theme.Fonts;
import com.staysync.ui.theme.Icons;
import com.staysync.ui.theme.Tokens;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Clean, production-grade login view for StaySync.
 * Faithful to the approved Google Stitch aesthetic:
 * - Warm background #FDFBD4, Card surface #FFFEF0, Forest Green primary #064E3B.
 * - Provides email & password inputs with keyboard Enter navigation.
 * - Dynamic error banner rendering for credential and validation errors.
 * - One-click demo credential chips for rapid manual verification.
 * - Live database status telemetry indicator.
 */
public class LoginPanel extends JPanel {

    private final AuthService authService;
    private final Consumer<CurrentUser> onLoginSuccess;

    private JTextField emailField;
    private JPasswordField passwordField;
    private AppButton loginButton;
    private JPanel errorContainer;
    private ErrorBanner errorBanner;
    private JLabel statusLabel;

    public LoginPanel(AuthService authService, Consumer<CurrentUser> onLoginSuccess) {
        super(new BorderLayout());
        this.authService = Objects.requireNonNull(authService, "AuthService must not be null");
        this.onLoginSuccess = Objects.requireNonNull(onLoginSuccess, "Login success callback must not be null");

        setBackground(Tokens.bg()); // #FDFBD4

        initUI();
    }

    private void initUI() {
        // Center wrapper: centers login card both vertically and horizontally
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(UIScale.scale(24), UIScale.scale(24), UIScale.scale(24), UIScale.scale(24));

        centerWrapper.add(createLoginCard(), gbc);
        add(centerWrapper, BorderLayout.CENTER);

        // Bottom telemetry bar
        add(createBottomTelemetryBar(), BorderLayout.SOUTH);
    }

    private JPanel createLoginCard() {
        Card card = new Card(new BorderLayout(), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(Tokens.space(6), Tokens.space(6), Tokens.space(6), Tokens.space(6))
        ));

        int cardWidth = UIScale.scale(440);
        card.setPreferredSize(new Dimension(cardWidth, UIScale.scale(610)));
        card.setMinimumSize(new Dimension(cardWidth, UIScale.scale(560)));
        card.setMaximumSize(new Dimension(cardWidth, UIScale.scale(650)));

        JPanel cardContent = new JPanel();
        cardContent.setOpaque(false);
        cardContent.setLayout(new BoxLayout(cardContent, BoxLayout.Y_AXIS));

        // 1. BRAND HEADER: Logo + Brand Name + Subtitle
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JPanel brandIcon = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Tokens.forestGreen());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIScale.scale(12), UIScale.scale(12));
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(UIScale.scale(2.5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.drawArc(cx - UIScale.scale(10), cy - UIScale.scale(6), UIScale.scale(20), UIScale.scale(20), 40, 100);
                g2.drawArc(cx - UIScale.scale(6), cy - UIScale.scale(2), UIScale.scale(12), UIScale.scale(12), 40, 100);
                g2.fillOval(cx - UIScale.scale(2), cy + UIScale.scale(5), UIScale.scale(5), UIScale.scale(5));
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(UIScale.scale(46), UIScale.scale(46));
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        brandIcon.setAlignmentX(CENTER_ALIGNMENT);

        JLabel brandName = new JLabel("StaySync");
        brandName.setFont(Fonts.h1());
        brandName.setForeground(Tokens.text());
        brandName.setAlignmentX(CENTER_ALIGNMENT);

        JLabel tagline = new JLabel("Your Hostel. In Sync.");
        tagline.setFont(Fonts.smMedium());
        tagline.setForeground(Tokens.forestGreen());
        tagline.setAlignmentX(CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Sign in to your student or administrative desk");
        subtitle.setFont(Fonts.xs());
        subtitle.setForeground(Tokens.textMuted());
        subtitle.setAlignmentX(CENTER_ALIGNMENT);

        headerPanel.add(brandIcon);
        headerPanel.add(Box.createVerticalStrut(Tokens.space(2)));
        headerPanel.add(brandName);
        headerPanel.add(Box.createVerticalStrut(Tokens.space(1)));
        headerPanel.add(tagline);
        headerPanel.add(Box.createVerticalStrut(2));
        headerPanel.add(subtitle);

        cardContent.add(headerPanel);
        cardContent.add(Box.createVerticalStrut(Tokens.space(4)));

        // 2. ERROR BANNER CONTAINER
        errorContainer = new JPanel(new BorderLayout());
        errorContainer.setOpaque(false);
        errorBanner = new ErrorBanner("");
        errorContainer.add(errorBanner, BorderLayout.CENTER);
        errorContainer.setVisible(false);
        cardContent.add(errorContainer);
        cardContent.add(Box.createVerticalStrut(Tokens.space(2)));

        // 3. FORM FIELDS
        // Email Field
        JLabel emailLabel = new JLabel("CAMPUS EMAIL");
        emailLabel.setFont(Fonts.xs());
        emailLabel.setForeground(Tokens.textMuted());
        emailLabel.setAlignmentX(LEFT_ALIGNMENT);

        emailField = new JTextField();
        emailField.setFont(Fonts.body());
        emailField.setBackground(Tokens.surface());
        emailField.putClientProperty("JTextField.placeholderText", "e.g. bhavesh@staysync.internal or shrey@student.staysync");
        emailField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(12), UIScale.scale(8), UIScale.scale(12))
        ));
        emailField.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(38)));

        // Password Field
        JLabel passwordLabel = new JLabel("PASSWORD");
        passwordLabel.setFont(Fonts.xs());
        passwordLabel.setForeground(Tokens.textMuted());
        passwordLabel.setAlignmentX(LEFT_ALIGNMENT);

        passwordField = new JPasswordField();
        passwordField.setFont(Fonts.body());
        passwordField.setBackground(Tokens.surface());
        passwordField.putClientProperty("JTextField.placeholderText", "••••••••");
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(12), UIScale.scale(8), UIScale.scale(12))
        ));
        passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(38)));

        // Enter key navigation
        emailField.addActionListener(e -> {
            if (passwordField.getPassword().length > 0) {
                performLogin();
            } else {
                passwordField.requestFocusInWindow();
            }
        });
        passwordField.addActionListener(e -> performLogin());

        // Field panel
        JPanel fieldPanel = new JPanel();
        fieldPanel.setOpaque(false);
        fieldPanel.setLayout(new BoxLayout(fieldPanel, BoxLayout.Y_AXIS));
        fieldPanel.add(emailLabel);
        fieldPanel.add(Box.createVerticalStrut(Tokens.space(1)));
        fieldPanel.add(emailField);
        fieldPanel.add(Box.createVerticalStrut(Tokens.space(3)));
        fieldPanel.add(passwordLabel);
        fieldPanel.add(Box.createVerticalStrut(Tokens.space(1)));
        fieldPanel.add(passwordField);

        cardContent.add(fieldPanel);
        cardContent.add(Box.createVerticalStrut(Tokens.space(4)));

        // 4. SIGN IN BUTTON
        loginButton = new AppButton("Sign In to StaySync", AppButton.Variant.PRIMARY, AppButton.Size.LG);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(42)));
        loginButton.setAlignmentX(CENTER_ALIGNMENT);
        loginButton.addActionListener(e -> performLogin());

        cardContent.add(loginButton);
        cardContent.add(Box.createVerticalStrut(Tokens.space(4)));

        // 5. DEMO ACCOUNTS QUICK-FILL SECTION
        JPanel demoSection = new JPanel();
        demoSection.setOpaque(false);
        demoSection.setLayout(new BoxLayout(demoSection, BoxLayout.Y_AXIS));

        JLabel demoHeader = new JLabel("DEMO ACCOUNTS (ONE-CLICK FILL)");
        demoHeader.setFont(Fonts.xs());
        demoHeader.setForeground(Tokens.textFaint());
        demoHeader.setAlignmentX(CENTER_ALIGNMENT);

        JPanel demoButtonsRow = new JPanel(new GridLayout(2, 2, Tokens.space(2), Tokens.space(2)));
        demoButtonsRow.setOpaque(false);
        demoButtonsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(64)));

        JButton student1Btn = createDemoChip("Shrey Vora (Student)", "shrey@student.staysync", "password123");
        JButton student2Btn = createDemoChip("Aadi Sheth (Student)", "aadi@student.staysync", "password123");
        JButton wardenBtn = createDemoChip("Bhavesh Patel (Warden)", "bhavesh@staysync.internal", "password123");
        JButton staffBtn = createDemoChip("Shubhangi Keni (Staff)", "shubhangi@staysync.internal", "password123");

        demoButtonsRow.add(student1Btn);
        demoButtonsRow.add(student2Btn);
        demoButtonsRow.add(wardenBtn);
        demoButtonsRow.add(staffBtn);

        demoSection.add(demoHeader);
        demoSection.add(Box.createVerticalStrut(Tokens.space(1)));
        demoSection.add(demoButtonsRow);

        cardContent.add(demoSection);
        cardContent.add(Box.createVerticalStrut(Tokens.space(3)));

        // 6. ENCRYPTION BADGE FOOTER
        JPanel privacyLock = new JPanel(new FlowLayout(FlowLayout.CENTER, Tokens.space(1), 0));
        privacyLock.setOpaque(false);
        JLabel lockIcon = new JLabel(Icons.get("lock", 12, Tokens.forestGreen()));
        JLabel lockText = new JLabel("Protected by StaySync Single-Session Security");
        lockText.setFont(Fonts.xs());
        lockText.setForeground(Tokens.textMuted());
        privacyLock.add(lockIcon);
        privacyLock.add(lockText);

        cardContent.add(privacyLock);

        card.add(cardContent, BorderLayout.CENTER);
        return card;
    }

    private JButton createDemoChip(String label, String email, String password) {
        JButton btn = new JButton(label);
        btn.setFont(Fonts.xs());
        btn.setBackground(Tokens.bgSubtle());
        btn.setForeground(Tokens.forestGreen());
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(UIScale.scale(3), UIScale.scale(8), UIScale.scale(3), UIScale.scale(8))
        ));

        btn.addActionListener(e -> {
            emailField.setText(email);
            passwordField.setText(password);
            hideError();
            passwordField.requestFocusInWindow();
        });

        return btn;
    }

    private JPanel createBottomTelemetryBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Tokens.bg());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Tokens.border()),
                BorderFactory.createEmptyBorder(Tokens.space(2), Tokens.space(4), Tokens.space(2), Tokens.space(4))
        ));

        statusLabel = new JLabel("<html><span style='color: #D97706; font-weight: bold;'>●</span> Checking database connection...</html>");
        statusLabel.setFont(Fonts.xs());
        statusLabel.setForeground(Tokens.textMuted());

        JLabel appTitle = new JLabel("StaySync • Premises & Mess Resolution Suite");
        appTitle.setFont(Fonts.xs());
        appTitle.setForeground(Tokens.textMuted());

        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(appTitle, BorderLayout.EAST);
        return bar;
    }

    /**
     * Executes authentication against the backend service.
     */
    public void performLogin() {
        String email = emailField.getText();
        String password = new String(passwordField.getPassword());

        hideError();

        // 1. Client-side input validation
        if (email == null || email.isBlank() || password.isBlank()) {
            showError("Email and password are required.");
            if (email == null || email.isBlank()) {
                emailField.requestFocusInWindow();
            } else {
                passwordField.requestFocusInWindow();
            }
            return;
        }

        loginButton.setLoading(true);
        emailField.setEnabled(false);
        passwordField.setEnabled(false);

        try {
            AuthResult result = authService.login(email, password);
            if (result.success()) {
                CurrentUser user = result.user();
                onLoginSuccess.accept(user);
            } else {
                showError(result.message() != null ? result.message() : "Invalid email or password.");
            }
        } catch (ValidationException | AuthenticationException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            showError("Authentication error: " + ex.getMessage());
        } finally {
            loginButton.setLoading(false);
            emailField.setEnabled(true);
            passwordField.setEnabled(true);
        }
    }

    public void showError(String message) {
        errorBanner.setMessage(message != null ? message : "Invalid credentials.");
        errorContainer.setVisible(true);
        errorContainer.revalidate();
        errorContainer.repaint();
    }

    public void hideError() {
        errorContainer.setVisible(false);
        errorContainer.revalidate();
        errorContainer.repaint();
    }

    /**
     * Resets input fields and clears error banners upon logout.
     */
    public void reset() {
        emailField.setText("");
        passwordField.setText("");
        hideError();
        emailField.requestFocusInWindow();
    }

    /**
     * Updates the database connectivity status indicator.
     */
    public void updateDatabaseStatus(boolean connected, long pingMs) {
        updateDatabaseStatus(connected, pingMs, null);
    }

    public void updateDatabaseStatus(boolean connected, long pingMs, String message) {
        if (statusLabel != null) {
            if (connected) {
                statusLabel.setText("<html><span style='color: #064E3B; font-weight: bold;'>●</span> Connected to MySQL (staysync_db) &nbsp;•&nbsp; Sync: Live</html>");
                hideError();
            } else {
                statusLabel.setText("<html><span style='color: #D97706; font-weight: bold;'>●</span> Database Offline (MySQL Unreachable)</html>");
                String errorMsg = (message != null && !message.isBlank())
                        ? message
                        : "Database connection failed. Ensure MySQL Server is running and check config/app.properties.";
                showError(errorMsg);
            }
        }
    }
}
