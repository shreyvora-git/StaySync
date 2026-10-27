package com.staysync;

import com.formdev.flatlaf.util.UIScale;
import com.staysync.config.AppConfig;
import com.staysync.config.Prefs;
import com.staysync.dao.AnalyticsDao;
import com.staysync.dao.CategoryDao;
import com.staysync.dao.ComplaintDao;
import com.staysync.dao.HistoryDao;
import com.staysync.db.DataSourceProvider;
import com.staysync.model.Category;
import com.staysync.model.CategoryCount;
import com.staysync.model.CategoryUrgencyMapper;
import com.staysync.model.Complaint;
import com.staysync.model.ComplaintFilter;
import com.staysync.model.HistoryEntry;
import com.staysync.model.KpiSummary;
import com.staysync.model.Notification;
import com.staysync.model.Page;
import com.staysync.model.Priority;
import com.staysync.model.Role;
import com.staysync.model.Status;
import com.staysync.model.Urgency;
import com.staysync.security.CurrentUser;
import com.staysync.security.Session;
import com.staysync.service.AuthService;
import com.staysync.service.ComplaintService;
import com.staysync.service.NotificationService;
import com.staysync.ui.DesktopNotifier;
import com.staysync.ui.LoginPanel;
import com.staysync.ui.components.AppButton;
import com.staysync.ui.components.Card;
import com.staysync.ui.components.ChipLabel;
import com.staysync.ui.components.EmptyState;
import com.staysync.ui.components.ErrorBanner;
import com.staysync.ui.theme.Fonts;
import com.staysync.ui.theme.Icons;
import com.staysync.ui.theme.ThemeManager;
import com.staysync.ui.theme.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Main application entry point for StaySync (Hostel Mess Complaint Tracker).
 * Faithful native Java Swing reproduction of the approved Google Stitch design:
 * - Exact palette: Primary background #FDFBD4, Card surfaces #FFFEF0, Borders #DED6B9,
 *   Primary forest green #064E3B, Dark green #043D2E, Soft sage #DCE9DF, Text #292B26 / #66685F.
 * - Initial state displays the secure Login screen.
 * - Authenticated routing:
 *   - Role.STUDENT opens the Student Resident Panel with live complaints and filing.
 *   - Role.ADMIN opens the Administrative Console with live KPIs, queue triage, and analytics.
 * - Complete database-backed service integration (AuthService, ComplaintService, AnalyticsDao).
 * - Thread-safe single-session lifecycle with explicit logout capability.
 */
public final class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    // Backend Services & DAOs
    private static final AuthService authService = new AuthService();
    private static final ComplaintService complaintService = new ComplaintService();
    private static final AnalyticsDao analyticsDao = new AnalyticsDao();
    private static final CategoryDao categoryDao = new CategoryDao();
    private static final HistoryDao historyDao = new HistoryDao();
    private static final NotificationService notificationService = new NotificationService();

    // Primary Window & Top-Level State
    private static JFrame mainFrame;
    private static JPanel rootContainer;
    private static CardLayout rootCardLayout;
    private static LoginPanel loginPanel;
    private static JPanel authenticatedContainer;

    // Authenticated View State
    private static JPanel mainViewContainer;
    private static CardLayout mainCardLayout;
    private static String activeSidebarItem = "Live Dashboard";
    private static JPanel sidebarContainer;
    private static CurrentUser activeUser;

    // Notifications State & Polling
    private static javax.swing.Timer notificationPollingTimer;
    private static volatile long lastSeenNotificationId = 0L;
    private static volatile boolean notificationPollingInitialized = false;
    private static JLabel notificationBadgeLabel;
    private static JPanel bellPanel;
    private static JWindow activeToastWindow;

    // Filters State
    private static Long activeFilterCategoryId = null;
    private static Priority activeFilterPriority = null;
    private static Status activeFilterStatus = null;
    private static JButton topFilterBtn;

    // Top Navigation Dynamic Elements
    private static JLabel userNameLabel;
    private static JLabel userRoleLabel;
    private static JLabel avatarText;
    private static JLabel studentTab;
    private static JLabel adminTab;

    // Database Telemetry Status
    private static volatile boolean dbConnected = false;
    private static JLabel bottomStatusLabel;
    private static JLabel sidebarMeshLabel;
    private static JLabel sidebarPingLabel;

    public static boolean isDbConnected() {
        return dbConnected;
    }

    public static void main(String[] args) {
        // 1. Load application configuration
        AppConfig config = AppConfig.load();
        log.info("StaySync initializing (Config loaded: {})", config.isConfigLoaded());

        // 2. Register JVM shutdown hook for clean connection pool and tray disposal
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("StaySync JVM exiting. Cleaning up connection pool and tray icon...");
            DesktopNotifier.dispose();
            DataSourceProvider.shutdown();
        }, "StaySync-ShutdownHook"));

        // 3. Initialize theme and fonts before instantiating any UI elements
        ThemeManager.init(Prefs.getThemeMode());

        // 4. Launch UI on the Swing Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            JFrame frame = createStitchApplicationFrame();
            mainFrame = frame;
            DesktopNotifier.init(frame);
            frame.setVisible(true);

            // 5. Asynchronous database health check ping (off the EDT)
            runDatabaseStartupCheck();
        });
    }

    /**
     * Creates the primary StaySync desktop application window matching the approved Stitch reference.
     * The initial view is the Login Screen.
     */
    private static JFrame createStitchApplicationFrame() {
        mainFrame = new JFrame("StaySync — Your Hostel. In Sync. (Premises & Mess Resolution Suite)");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setMinimumSize(new Dimension(1120, 720));
        mainFrame.setSize(1400, 880);
        mainFrame.setLocationRelativeTo(null);

        Color bg = Tokens.bg();
        mainFrame.getContentPane().setBackground(bg);

        // Root container with CardLayout: "LOGIN" vs "AUTHENTICATED"
        rootCardLayout = new CardLayout();
        rootContainer = new JPanel(rootCardLayout);
        rootContainer.setBackground(bg);

        // 1. Login View (Initial view)
        loginPanel = new LoginPanel(authService, App::handleLoginSuccess);
        rootContainer.add(loginPanel, "LOGIN");

        // 2. Authenticated App Container
        authenticatedContainer = createAuthenticatedContainer();
        rootContainer.add(authenticatedContainer, "AUTHENTICATED");

        // Set initial screen to LOGIN
        rootCardLayout.show(rootContainer, "LOGIN");

        mainFrame.setContentPane(rootContainer);
        return mainFrame;
    }

    /**
     * Constructs the main application container holding top navigation, sidebar,
     * central card views, and bottom status telemetry.
     */
    private static JPanel createAuthenticatedContainer() {
        Color bg = Tokens.bg();
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(bg);

        // 1. Top Persistent Navigation Bar
        root.add(createTopNavBar(), BorderLayout.NORTH);

        // 2. Center Content (Left Sidebar + Main View Container)
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(bg);

        sidebarContainer = new JPanel(new BorderLayout());
        sidebarContainer.setBackground(bg);
        sidebarContainer.add(createOperationsSidebar(), BorderLayout.CENTER);
        centerPanel.add(sidebarContainer, BorderLayout.WEST);

        mainCardLayout = new CardLayout();
        mainViewContainer = new JPanel(mainCardLayout);
        mainViewContainer.setBackground(bg);

        centerPanel.add(mainViewContainer, BorderLayout.CENTER);
        root.add(centerPanel, BorderLayout.CENTER);

        // 3. Bottom Status Bar
        root.add(createBottomStatusBar(), BorderLayout.SOUTH);

        return root;
    }

    // ==========================================================================
    // AUTHENTICATION & SESSION LIFECYCLE HANDLERS
    // ==========================================================================

    private static void handleLoginSuccess(CurrentUser user) {
        log.info("Transitioning UI to authenticated session for [{}] ({})", user.email(), user.role());
        activeUser = user;

        // 1. Update top bar details
        updateTopNavBar(user);

        // 2. Populate and display role-specific views
        refreshViewsForRole(user);

        // 3. Update sidebar navigation items
        refreshSidebar();

        // 4. Start background notifications polling and initial check
        notificationPollingInitialized = false;
        lastSeenNotificationId = 0L;
        startNotificationPolling();

        // 5. Switch root layout to authenticated application
        rootCardLayout.show(rootContainer, "AUTHENTICATED");
    }

    private static void handleLogout() {
        log.info("Logging out current active session");
        stopNotificationPolling();
        notificationPollingInitialized = false;
        lastSeenNotificationId = 0L;

        // Clear active filters
        activeFilterCategoryId = null;
        activeFilterPriority = null;
        activeFilterStatus = null;
        if (topFilterBtn != null) {
            topFilterBtn.setBackground(null);
            topFilterBtn.setOpaque(false);
        }

        if (activeToastWindow != null) {
            activeToastWindow.dispose();
            activeToastWindow = null;
        }

        authService.logout();
        activeUser = null;
        if (loginPanel != null) {
            loginPanel.reset();
        }
        rootCardLayout.show(rootContainer, "LOGIN");
    }

    private static void updateTopNavBar(CurrentUser user) {
        if (user == null) return;

        if (userNameLabel != null) {
            userNameLabel.setText(user.fullName());
        }
        if (userRoleLabel != null) {
            if (user.isStudent()) {
                userRoleLabel.setText("Student Resident • " + user.email());
            } else {
                userRoleLabel.setText(user.canViewConfidential()
                        ? "Warden & Mess Committee (Full Clearance)"
                        : "Hostel Operations Staff");
            }
        }
        if (avatarText != null) {
            avatarText.setText(computeInitials(user.fullName()));
        }

        if (studentTab != null && adminTab != null) {
            if (user.isStudent()) {
                studentTab.setBackground(Tokens.forestGreen());
                studentTab.setForeground(Color.WHITE);
                studentTab.setOpaque(true);

                adminTab.setBackground(Tokens.bgSubtle());
                adminTab.setForeground(Tokens.textMuted());
                adminTab.setOpaque(false);
                adminTab.setVisible(false); // Conceal admin console for students
            } else {
                adminTab.setBackground(Tokens.forestGreen());
                adminTab.setForeground(Color.WHITE);
                adminTab.setOpaque(true);
                adminTab.setVisible(true);

                studentTab.setBackground(Tokens.bgSubtle());
                studentTab.setForeground(Tokens.textMuted());
                studentTab.setOpaque(false);
            }
        }
    }

    private static void refreshViewsForRole(CurrentUser user) {
        if (mainViewContainer == null || user == null) return;

        mainViewContainer.removeAll();

        if (user.isStudent()) {
            activeSidebarItem = "My Complaints";
            mainViewContainer.add(createStudentDashboardScreen(), "My Complaints");
            mainViewContainer.add(createStudentFileComplaintScreen(), "File a Complaint");
            mainViewContainer.add(createAnalyticsScrollableScreen(), "Hostel Charter & SLA");
            mainCardLayout.show(mainViewContainer, "My Complaints");
        } else {
            activeSidebarItem = "Live Dashboard";
            mainViewContainer.add(createDashboardScrollableScreen(), "Live Dashboard");
            mainViewContainer.add(createComplaintQueueScrollableScreen(), "Active Issues");
            mainViewContainer.add(createAnalyticsScrollableScreen(), "Mess Operations");
            mainViewContainer.add(createAuditLogsScrollableScreen(), "Audit Logs");
            mainCardLayout.show(mainViewContainer, "Live Dashboard");
        }

        mainViewContainer.revalidate();
        mainViewContainer.repaint();
    }

    // ==========================================================================
    // TOP NAVIGATION BAR
    // ==========================================================================

    private static JPanel createTopNavBar() {
        JPanel navBar = new JPanel(new BorderLayout(Tokens.space(4), 0));
        navBar.setBackground(Tokens.bg()); // #FDFBD4
        navBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.border()),
                BorderFactory.createEmptyBorder(Tokens.space(3), Tokens.space(4), Tokens.space(3), Tokens.space(5))
        ));

        // Left Section: StaySync Brand Logo + Tagline + Student/Admin Segmented Pill
        JPanel leftSection = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(3), 0));
        leftSection.setOpaque(false);

        // Brand Icon (Forest green rounded square with white arch symbol)
        JPanel brandIcon = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Tokens.forestGreen());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIScale.scale(10), UIScale.scale(10));
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(UIScale.scale(2.2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.drawArc(cx - UIScale.scale(8), cy - UIScale.scale(5), UIScale.scale(16), UIScale.scale(16), 40, 100);
                g2.drawArc(cx - UIScale.scale(5), cy - UIScale.scale(2), UIScale.scale(10), UIScale.scale(10), 40, 100);
                g2.fillOval(cx - UIScale.scale(2), cy + UIScale.scale(4), UIScale.scale(4), UIScale.scale(4));
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(UIScale.scale(36), UIScale.scale(36));
            }
        };
        brandIcon.setOpaque(false);

        // Brand Text Lockup
        JPanel brandTextPanel = new JPanel();
        brandTextPanel.setOpaque(false);
        brandTextPanel.setLayout(new BoxLayout(brandTextPanel, BoxLayout.Y_AXIS));

        JLabel brandName = new JLabel("StaySync");
        brandName.setFont(Fonts.h2());
        brandName.setForeground(Tokens.text());

        JLabel tagline = new JLabel("Your Hostel. In Sync.");
        tagline.setFont(Fonts.xs());
        tagline.setForeground(Tokens.textMuted());

        brandTextPanel.add(brandName);
        brandTextPanel.add(tagline);

        // Segmented Role Switcher: [ Student Portal ] [ Admin Console ]
        JPanel rolePill = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rolePill.setBackground(Tokens.bgSubtle());
        rolePill.setOpaque(true);
        rolePill.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        studentTab = new JLabel("Student Portal");
        studentTab.setFont(Fonts.smMedium());
        studentTab.setForeground(Color.WHITE);
        studentTab.setBackground(Tokens.forestGreen());
        studentTab.setOpaque(true);
        studentTab.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(14), UIScale.scale(4), UIScale.scale(14)));

        adminTab = new JLabel("Admin Console");
        adminTab.setFont(Fonts.sm());
        adminTab.setForeground(Tokens.textMuted());
        adminTab.setOpaque(false);
        adminTab.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(12), UIScale.scale(4), UIScale.scale(12)));

        rolePill.add(studentTab);
        rolePill.add(adminTab);

        leftSection.add(brandIcon);
        leftSection.add(brandTextPanel);
        leftSection.add(Box.createHorizontalStrut(Tokens.space(3)));
        leftSection.add(rolePill);

        // Right Section: Utility icons + Dynamic Profile Lockup + Sign Out Button
        JPanel rightSection = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.space(3), 0));
        rightSection.setOpaque(false);

        // Filter button
        topFilterBtn = createIconButton("filter", 16);
        topFilterBtn.setToolTipText("Filter Complaints by Category, Priority, or Status");
        topFilterBtn.addActionListener(e -> showFilterDialog());

        // Notification Bell with alert dot
        bellPanel = new JPanel(new BorderLayout());
        bellPanel.setOpaque(false);
        bellPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bellPanel.setToolTipText("View Notifications");

        JLabel bellIcon = new JLabel(Icons.get("bell", 16, Tokens.text()));
        notificationBadgeLabel = new JLabel("●");
        notificationBadgeLabel.setFont(Fonts.xs());
        notificationBadgeLabel.setForeground(new Color(0xDC, 0x26, 0x26));
        notificationBadgeLabel.setVisible(false);

        bellPanel.add(bellIcon, BorderLayout.CENTER);
        bellPanel.add(notificationBadgeLabel, BorderLayout.NORTH);
        bellPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showNotificationDropdown(bellPanel);
            }
        });

        // Theme Toggle button
        JButton themeBtn = createIconButton("sun", 16);
        themeBtn.addActionListener(e -> {
            ThemeManager.toggleTheme();
            themeBtn.setIcon(Icons.get(ThemeManager.isDark() ? "sun" : "moon", 16, Tokens.text()));
        });

        // Vertical Divider
        JLabel divider = new JLabel("|");
        divider.setForeground(Tokens.border());

        // User Profile Lockup (Dynamic)
        JPanel userLockup = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), 0));
        userLockup.setOpaque(false);

        // Circular Avatar with dynamic initials
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xD9, 0x77, 0x06)); // Warm amber avatar circle
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(UIScale.scale(28), UIScale.scale(28));
            }
        };
        avatar.setLayout(new GridBagLayout());
        avatar.setOpaque(false);

        avatarText = new JLabel("U");
        avatarText.setFont(Fonts.xs());
        avatarText.setForeground(Color.WHITE);
        avatar.add(avatarText);

        JPanel userText = new JPanel();
        userText.setOpaque(false);
        userText.setLayout(new BoxLayout(userText, BoxLayout.Y_AXIS));

        userNameLabel = new JLabel("Guest User");
        userNameLabel.setFont(Fonts.smMedium());
        userNameLabel.setForeground(Tokens.text());

        userRoleLabel = new JLabel("Authenticated Session");
        userRoleLabel.setFont(Fonts.xs());
        userRoleLabel.setForeground(Tokens.textMuted());

        userText.add(userNameLabel);
        userText.add(userRoleLabel);

        userLockup.add(avatar);
        userLockup.add(userText);

        // Second Divider
        JLabel divider2 = new JLabel("|");
        divider2.setForeground(Tokens.border());

        // Prominent Sign Out Button
        AppButton signOutBtn = new AppButton("Sign Out", AppButton.Variant.GHOST, AppButton.Size.SM);
        signOutBtn.setIcon(Icons.get("user", 13, Tokens.text()));
        signOutBtn.setToolTipText("Sign out of active session");
        signOutBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    mainFrame,
                    "Are you sure you want to sign out?",
                    "StaySync — Confirm Sign Out",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                handleLogout();
            }
        });

        rightSection.add(topFilterBtn);
        rightSection.add(bellPanel);
        rightSection.add(themeBtn);
        rightSection.add(divider);
        rightSection.add(userLockup);
        rightSection.add(divider2);
        rightSection.add(signOutBtn);

        navBar.add(leftSection, BorderLayout.WEST);
        navBar.add(rightSection, BorderLayout.EAST);
        return navBar;
    }

    private static JButton createIconButton(String iconName, int size) {
        JButton btn = new JButton(Icons.get(iconName, size, Tokens.text()));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(UIScale.scale(28), UIScale.scale(28)));
        return btn;
    }

    // ==========================================================================
    // OPERATIONS SIDEBAR
    // ==========================================================================

    private static JPanel createOperationsSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Tokens.bg()); // #FDFBD4
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Tokens.border()),
                BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(3), Tokens.space(4), Tokens.space(3))
        ));
        int sbWidth = UIScale.scale(236);
        sidebar.setPreferredSize(new Dimension(sbWidth, 0));
        sidebar.setMinimumSize(new Dimension(sbWidth, 0));

        // Top Section: Title + Nav Buttons
        JPanel topPanel = new JPanel();
        topPanel.setOpaque(false);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        boolean isStudent = activeUser != null && activeUser.isStudent();

        JLabel title = new JLabel(isStudent ? "STUDENT RESIDENT DESK" : "OPERATIONS DESK");
        title.setFont(Fonts.xs());
        title.setForeground(Tokens.textMuted());
        title.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(8), Tokens.space(2), 0));
        topPanel.add(title);

        if (isStudent) {
            topPanel.add(createSidebarNavItem("My Complaints", "layout-dashboard"));
            topPanel.add(Box.createVerticalStrut(Tokens.space(1)));
            topPanel.add(createSidebarNavItem("File a Complaint", "plus"));
            topPanel.add(Box.createVerticalStrut(Tokens.space(1)));
            topPanel.add(createSidebarNavItem("Hostel Charter & SLA", "shield"));
        } else {
            topPanel.add(createSidebarNavItem("Live Dashboard", "layout-dashboard"));
            topPanel.add(Box.createVerticalStrut(Tokens.space(1)));
            topPanel.add(createSidebarNavItem("Active Issues", "alert-triangle"));
            topPanel.add(Box.createVerticalStrut(Tokens.space(1)));
            topPanel.add(createSidebarNavItem("Mess Operations", "utensils"));
            topPanel.add(Box.createVerticalStrut(Tokens.space(1)));
            topPanel.add(createSidebarNavItem("Audit Logs", "history"));
        }

        sidebar.add(topPanel, BorderLayout.NORTH);

        // Center Spacer (pushes connection badge to absolute bottom)
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        sidebar.add(spacer, BorderLayout.CENTER);

        // Bottom Section: Campus Mesh / Database Connection Badge
        JPanel bottomBadge = new JPanel(new BorderLayout());
        bottomBadge.setBackground(Tokens.bgSubtle());
        bottomBadge.setOpaque(true);
        bottomBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(UIScale.scale(6), UIScale.scale(10), UIScale.scale(6), UIScale.scale(10))
        ));

        sidebarMeshLabel = new JLabel(dbConnected ? "<html><span style='color: #064E3B; font-weight: bold;'>●</span>  Campus Mesh V2</html>" : "●  Connecting...");
        sidebarMeshLabel.setFont(Fonts.xs());
        sidebarMeshLabel.setForeground(dbConnected ? Tokens.forestGreen() : Tokens.textMuted());

        sidebarPingLabel = new JLabel(dbConnected ? "3ms ping" : "checking...");
        sidebarPingLabel.setFont(Fonts.xs());
        sidebarPingLabel.setForeground(Tokens.textFaint());

        bottomBadge.add(sidebarMeshLabel, BorderLayout.WEST);
        bottomBadge.add(sidebarPingLabel, BorderLayout.EAST);

        sidebar.add(bottomBadge, BorderLayout.SOUTH);
        return sidebar;
    }

    private static JPanel createSidebarNavItem(String name, String iconName) {
        boolean isActive = name.equals(activeSidebarItem);
        JPanel item = new JPanel(new BorderLayout(Tokens.space(2), 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (name.equals(activeSidebarItem)) {
                    g2.setColor(Tokens.forestGreen());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIScale.scale(8), UIScale.scale(8));
                } else if (getBackground() != null && !getBackground().equals(Tokens.bg())) {
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIScale.scale(8), UIScale.scale(8));
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        item.setOpaque(false);
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color fg = isActive ? Color.WHITE : Tokens.text();
        item.setBackground(isActive ? Tokens.forestGreen() : Tokens.bg());
        item.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(6), UIScale.scale(10), UIScale.scale(6), UIScale.scale(10)));
        int h = UIScale.scale(38);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        item.setPreferredSize(new Dimension(UIScale.scale(212), h));

        JLabel iconLbl = new JLabel(Icons.get(iconName, 16, fg));
        JLabel textLbl = new JLabel(name);
        textLbl.setFont(isActive ? Fonts.smMedium() : Fonts.sm());
        textLbl.setForeground(fg);

        item.add(iconLbl, BorderLayout.WEST);
        item.add(textLbl, BorderLayout.CENTER);

        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                activeSidebarItem = name;
                if ("File a Complaint".equals(name)) {
                    mainCardLayout.show(mainViewContainer, "File a Complaint");
                } else if ("My Complaints".equals(name)) {
                    mainCardLayout.show(mainViewContainer, "My Complaints");
                } else if ("Hostel Charter & SLA".equals(name)) {
                    mainCardLayout.show(mainViewContainer, "Hostel Charter & SLA");
                } else if ("Active Issues".equals(name)) {
                    mainCardLayout.show(mainViewContainer, "Active Issues");
                } else if ("Mess Operations".equals(name)) {
                    mainCardLayout.show(mainViewContainer, "Mess Operations");
                } else if ("Audit Logs".equals(name)) {
                    mainCardLayout.show(mainViewContainer, "Audit Logs");
                } else {
                    mainCardLayout.show(mainViewContainer, "Live Dashboard");
                }
                refreshSidebar();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!name.equals(activeSidebarItem)) {
                    item.setBackground(Tokens.bgSubtle());
                    item.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!name.equals(activeSidebarItem)) {
                    item.setBackground(Tokens.bg());
                    item.repaint();
                }
            }
        });

        return item;
    }

    private static void refreshSidebar() {
        if (sidebarContainer != null) {
            sidebarContainer.removeAll();
            sidebarContainer.add(createOperationsSidebar(), BorderLayout.CENTER);
            sidebarContainer.revalidate();
            sidebarContainer.repaint();
        }
    }

    /**
     * ScrollablePanel implements javax.swing.Scrollable to guarantee that its width
     * strictly tracks the JViewport width, permanently preventing horizontal scrolling.
     */
    private static class ScrollablePanel extends JPanel implements javax.swing.Scrollable {
        public ScrollablePanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return UIScale.scale(20);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return UIScale.scale(60);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // ==========================================================================
    // SCREEN A (ADMIN) — LIVE DASHBOARD WITH DATABASE-BACKED METRICS
    // ==========================================================================

    private static JScrollPane createDashboardScrollableScreen() {
        ScrollablePanel scrollContent = new ScrollablePanel(new BorderLayout());
        scrollContent.setBackground(Tokens.bg());
        scrollContent.setOpaque(true);

        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(6), Tokens.space(4)));

        // 1. Welcome Hero Banner (Personalized)
        container.add(createWelcomeHeroBanner());
        container.add(Box.createVerticalStrut(Tokens.space(4)));

        // 2. Four KPI Cards Row (Live Database Summary)
        container.add(createKpiCardsRow());
        container.add(Box.createVerticalStrut(Tokens.space(4)));

        // 3. Recent Complaints Activity Table (Live Records)
        container.add(createRecentComplaintsTableCard(true));
        container.add(Box.createVerticalStrut(Tokens.space(4)));

        // 4. Analytics & Privacy 3-Card Deck (Live Category Breakdown)
        container.add(createAnalyticsAndPrivacyDeck());

        scrollContent.add(container, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(true);
        scrollPane.setBackground(Tokens.bg());
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(Tokens.bg());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        return scrollPane;
    }

    // ==========================================================================
    // SCREEN B (ADMIN) — FOCUSED COMPLAINT QUEUE
    // ==========================================================================

    private static JScrollPane createComplaintQueueScrollableScreen() {
        ScrollablePanel scrollContent = new ScrollablePanel(new BorderLayout());
        scrollContent.setBackground(Tokens.bg());
        scrollContent.setOpaque(true);

        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(6), Tokens.space(4)));

        // Focused Table Card (Full queue view)
        container.add(createRecentComplaintsTableCard(false));

        scrollContent.add(container, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(true);
        scrollPane.setBackground(Tokens.bg());
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(Tokens.bg());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        return scrollPane;
    }

    // ==========================================================================
    // SCREEN C — ANALYTICS & PRIVACY VIEW
    // ==========================================================================

    private static JScrollPane createAnalyticsScrollableScreen() {
        ScrollablePanel scrollContent = new ScrollablePanel(new BorderLayout());
        scrollContent.setBackground(Tokens.bg());
        scrollContent.setOpaque(true);

        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(6), Tokens.space(4)));

        container.add(createAnalyticsAndPrivacyDeck());

        scrollContent.add(container, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(true);
        scrollPane.setBackground(Tokens.bg());
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(Tokens.bg());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        return scrollPane;
    }

    // ==========================================================================
    // STUDENT VIEW: DASHBOARD SCREEN (MY COMPLAINTS & LIVE KPIS)
    // ==========================================================================

    private static JScrollPane createStudentDashboardScreen() {
        ScrollablePanel scrollContent = new ScrollablePanel(new BorderLayout());
        scrollContent.setBackground(Tokens.bg());
        scrollContent.setOpaque(true);

        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(6), Tokens.space(4)));

        // 1. Student Hero Banner
        container.add(createStudentWelcomeHeroBanner());
        container.add(Box.createVerticalStrut(Tokens.space(4)));

        // Fetch student's own complaints (with active filters applied)
        Page<Complaint> page;
        try {
            ComplaintFilter filter = new ComplaintFilter(
                null,
                activeFilterStatus,
                activeFilterCategoryId,
                activeFilterPriority,
                null,
                null,
                0,
                50,
                "createdAt",
                false
            );
            page = complaintService.listComplaints(filter);
        } catch (Exception e) {
            log.warn("Failed to load student complaints: {}", e.getMessage());
            page = Page.empty(0, 50);
        }

        List<Complaint> myComplaints = page.items();
        long pending = myComplaints.stream().filter(c -> c.status() == Status.PENDING).count();
        long inProgress = myComplaints.stream().filter(c -> c.status() == Status.IN_PROGRESS).count();
        long resolved = myComplaints.stream().filter(c -> c.status() == Status.RESOLVED).count();
        long total = myComplaints.size();

        // 2. Student KPI Cards
        container.add(createStudentKpiCardsRow(total, pending, inProgress, resolved));
        container.add(Box.createVerticalStrut(Tokens.space(4)));

        // 3. Student Complaints Table Card
        container.add(createStudentComplaintsTableCard(myComplaints, page.totalItems()));

        scrollContent.add(container, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(true);
        scrollPane.setBackground(Tokens.bg());
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(Tokens.bg());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        return scrollPane;
    }

    private static Card createStudentWelcomeHeroBanner() {
        Card card = new Card(new BorderLayout(Tokens.space(3), 0), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(5), Tokens.space(4), Tokens.space(5)));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Tokens.space(3)));

        JPanel topBadgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), 0));
        topBadgeRow.setOpaque(false);

        JLabel statusPill = new JLabel("●  Student Portal Active");
        statusPill.setFont(Fonts.xs());
        statusPill.setForeground(Tokens.forestGreen());
        statusPill.setBackground(Tokens.softSage());
        statusPill.setOpaque(true);
        statusPill.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JLabel termLabel = new JLabel("Campus Living • Verified Resident");
        termLabel.setFont(Fonts.xs());
        termLabel.setForeground(Tokens.textMuted());

        topBadgeRow.add(statusPill);
        topBadgeRow.add(termLabel);

        String greeting = "Welcome back, " + (activeUser != null ? activeUser.fullName() : "Resident") + ".";
        JLabel headline = new JLabel(greeting);
        headline.setFont(Fonts.h1());
        headline.setForeground(Tokens.text());

        JLabel subtext = new JLabel("<html><body style='width: 100%; color: #66685F;'>"
                + "Track the status of your reported issues, inspect updates from staff, or lodge a new hostel complaint."
                + "</body></html>");
        subtext.setFont(Fonts.body());

        left.add(topBadgeRow);
        left.add(Box.createVerticalStrut(Tokens.space(2)));
        left.add(headline);
        left.add(Box.createVerticalStrut(Tokens.space(1)));
        left.add(subtext);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.space(2), 0));
        rightButtons.setOpaque(false);

        AppButton refreshBtn = new AppButton("Refresh", AppButton.Variant.SECONDARY, AppButton.Size.SM);
        refreshBtn.setIcon(Icons.get("refresh-cw", 13, Tokens.text()));
        refreshBtn.addActionListener(e -> {
            if (activeUser != null) refreshViewsForRole(activeUser);
        });

        AppButton fileBtn = new AppButton("+ File a Complaint", AppButton.Variant.PRIMARY, AppButton.Size.SM);
        fileBtn.addActionListener(e -> {
            activeSidebarItem = "File a Complaint";
            mainCardLayout.show(mainViewContainer, "File a Complaint");
            refreshSidebar();
        });

        rightButtons.add(refreshBtn);
        rightButtons.add(fileBtn);

        card.add(left, BorderLayout.CENTER);
        card.add(rightButtons, BorderLayout.EAST);
        return card;
    }

    private static JPanel createStudentKpiCardsRow(long total, long pending, long inProgress, long resolved) {
        JPanel row = new JPanel(new GridLayout(1, 4, Tokens.space(3), 0));
        row.setOpaque(false);

        row.add(createKpiCard(
                "ACTIVE ISSUES",
                "hourglass",
                new Color(0xD9, 0x77, 0x06),
                new Color(0xFE, 0xF3, 0xC7),
                String.valueOf(pending + inProgress),
                "Under Action",
                new Color(0xB4, 0x53, 0x09),
                "Pending staff review & dispatch"
        ));

        row.add(createKpiCard(
                "IN PROGRESS",
                "wrench",
                Tokens.forestGreen(),
                Tokens.softSage(),
                String.valueOf(inProgress),
                "Dispatched",
                Tokens.forestGreen(),
                "Maintenance crew active"
        ));

        row.add(createKpiCard(
                "RESOLVED",
                "check",
                Tokens.forestGreen(),
                Tokens.softSage(),
                String.valueOf(resolved),
                "Completed",
                Tokens.forestGreen(),
                "Closed with verified notes"
        ));

        row.add(createKpiCard(
                "TOTAL REPORTED",
                "sliders",
                Color.WHITE,
                Tokens.forestGreen(),
                String.valueOf(total),
                "Lifetime Filed",
                Tokens.text(),
                "Your registered tickets"
        ));

        return row;
    }

    private static Card createStudentComplaintsTableCard(List<Complaint> complaints, long totalItems) {
        Card card = new Card(new BorderLayout(0, Tokens.space(3)), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(5), Tokens.space(4), Tokens.space(5)));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), 0));
        leftHeader.setOpaque(false);

        JLabel title = new JLabel("●  My Submitted Complaints");
        title.setFont(Fonts.h2());
        title.setForeground(Tokens.text());

        String filterSuffix = (activeFilterCategoryId != null || activeFilterPriority != null || activeFilterStatus != null) ? " (Filtered)" : "";
        JLabel countBadge = new JLabel("Showing " + complaints.size() + " of " + totalItems + filterSuffix);
        countBadge.setFont(Fonts.xs());
        countBadge.setForeground(Tokens.textMuted());
        countBadge.setBackground(Tokens.bgSubtle());
        countBadge.setOpaque(true);
        countBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        leftHeader.add(title);
        leftHeader.add(countBadge);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.space(2), 0));
        rightHeader.setOpaque(false);

        JButton tableFilterBtn = createIconButton("filter", 12);
        tableFilterBtn.setBorder(BorderFactory.createLineBorder(Tokens.border(), 1, true));
        tableFilterBtn.setToolTipText("Filter Complaints");
        tableFilterBtn.addActionListener(e -> showFilterDialog());

        JButton refreshBtn = createIconButton("refresh-cw", 12);
        refreshBtn.setBorder(BorderFactory.createLineBorder(Tokens.border(), 1, true));
        refreshBtn.addActionListener(e -> {
            if (activeUser != null) refreshViewsForRole(activeUser);
        });

        rightHeader.add(tableFilterBtn);
        rightHeader.add(refreshBtn);

        header.add(leftHeader, BorderLayout.WEST);
        header.add(rightHeader, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        if (complaints.isEmpty()) {
            EmptyState emptyState = new EmptyState(
                    "No Complaints",
                    "Filed Yet",
                    "You have not reported any hostel or mess issues.",
                    "+ File a Complaint",
                    () -> {
                        activeSidebarItem = "File a Complaint";
                        mainCardLayout.show(mainViewContainer, "File a Complaint");
                        refreshSidebar();
                    }
            );
            card.add(emptyState, BorderLayout.CENTER);
        } else {
            JPanel tablePanel = new JPanel();
            tablePanel.setOpaque(false);
            tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.Y_AXIS));

            tablePanel.add(createTableHeaderRow());

            for (Complaint c : complaints) {
                tablePanel.add(createTableDataRow(c, false));
            }

            card.add(tablePanel, BorderLayout.CENTER);
        }

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(Tokens.space(3), 0, 0, 0));

        JLabel footnote = new JLabel("✓  Your reports are confidential and protected under Campus Living Bylaw 4.2.");
        footnote.setFont(Fonts.xs());
        footnote.setForeground(Tokens.textMuted());

        footer.add(footnote, BorderLayout.WEST);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    // ==========================================================================
    // STUDENT VIEW: FILE COMPLAINT SCREEN
    // ==========================================================================

    private static JScrollPane createStudentFileComplaintScreen() {
        ScrollablePanel scrollContent = new ScrollablePanel(new BorderLayout());
        scrollContent.setBackground(Tokens.bg());
        scrollContent.setOpaque(true);

        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(6), Tokens.space(4)));

        Card card = new Card(new BorderLayout(), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(Tokens.space(5), Tokens.space(6), Tokens.space(5), Tokens.space(6))
        ));

        JPanel formContent = new JPanel();
        formContent.setOpaque(false);
        formContent.setLayout(new BoxLayout(formContent, BoxLayout.Y_AXIS));

        JLabel formTitle = new JLabel("●  Report a New Hostel or Mess Issue");
        formTitle.setFont(Fonts.h1());
        formTitle.setForeground(Tokens.text());

        JLabel formSub = new JLabel("Provide clear details to enable prompt inspection and staff assignment.");
        formSub.setFont(Fonts.body());
        formSub.setForeground(Tokens.textMuted());

        formContent.add(formTitle);
        formContent.add(Box.createVerticalStrut(Tokens.space(1)));
        formContent.add(formSub);
        formContent.add(Box.createVerticalStrut(Tokens.space(4)));

        // Error Banner inside form
        JPanel errorBox = new JPanel(new BorderLayout());
        errorBox.setOpaque(false);
        ErrorBanner formError = new ErrorBanner("");
        errorBox.add(formError, BorderLayout.CENTER);
        errorBox.setVisible(false);
        formContent.add(errorBox);
        formContent.add(Box.createVerticalStrut(Tokens.space(2)));

        // 1. Title Input
        JLabel titleLbl = new JLabel("ISSUE SUMMARY / TITLE (5 to 150 characters)");
        titleLbl.setFont(Fonts.xs());
        titleLbl.setForeground(Tokens.textMuted());

        JTextField titleField = new JTextField();
        titleField.setFont(Fonts.body());
        titleField.setBackground(Tokens.surface());
        titleField.putClientProperty("JTextField.placeholderText", "e.g. Broken window latch allowing rain seepage");
        titleField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(10), UIScale.scale(8), UIScale.scale(10))
        ));
        titleField.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(38)));

        // 2. Category Dropdown
        JLabel catLbl = new JLabel("COMPLAINT CATEGORY");
        catLbl.setFont(Fonts.xs());
        catLbl.setForeground(Tokens.textMuted());

        List<Category> activeCategories;
        try {
            activeCategories = categoryDao.findActive();
        } catch (Exception e) {
            log.warn("Could not load categories: {}", e.getMessage());
            activeCategories = List.of();
        }

        JComboBox<Category> categoryCombo = new JComboBox<>(activeCategories.toArray(new Category[0]));
        categoryCombo.setFont(Fonts.body());
        categoryCombo.setBackground(Tokens.surface());
        categoryCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(38)));
        categoryCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Category cat) {
                    setText(cat.categoryName() + (cat.isConfidential() ? " 🔒 (Confidential)" : ""));
                }
                return this;
            }
        });

        // 3. Location Input
        JLabel locLbl = new JLabel("LOCATION NOTE (Optional, up to 100 chars)");
        locLbl.setFont(Fonts.xs());
        locLbl.setForeground(Tokens.textMuted());

        JTextField locField = new JTextField();
        locField.setFont(Fonts.body());
        locField.setBackground(Tokens.surface());
        locField.putClientProperty("JTextField.placeholderText", "e.g. Block B, Room 304 or Dining Hall 2 Counter A");
        locField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(10), UIScale.scale(8), UIScale.scale(10))
        ));
        locField.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(38)));

        // 4. System-Assigned Urgency (Read-only badge determined automatically by category policy)
        JLabel urgLbl = new JLabel("SYSTEM-ASSIGNED URGENCY");
        urgLbl.setFont(Fonts.xs());
        urgLbl.setForeground(Tokens.textMuted());

        JPanel urgencyContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), 0));
        urgencyContainer.setOpaque(false);
        urgencyContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(32)));

        Category initialCat = (Category) categoryCombo.getSelectedItem();
        Urgency initialUrg = CategoryUrgencyMapper.getUrgency(initialCat);
        final JPanel[] badgeWrapper = new JPanel[]{ new JPanel(new BorderLayout()) };
        badgeWrapper[0].setOpaque(false);
        badgeWrapper[0].add(ChipLabel.urgency(initialUrg), BorderLayout.CENTER);

        JLabel urgNote = new JLabel("Determined automatically by hostel SLA category policy");
        urgNote.setFont(Fonts.xs());
        urgNote.setForeground(Tokens.textMuted());

        urgencyContainer.add(badgeWrapper[0]);
        urgencyContainer.add(urgNote);

        categoryCombo.addActionListener(e -> {
            Category cat = (Category) categoryCombo.getSelectedItem();
            Urgency urg = CategoryUrgencyMapper.getUrgency(cat);
            badgeWrapper[0].removeAll();
            badgeWrapper[0].add(ChipLabel.urgency(urg), BorderLayout.CENTER);
            badgeWrapper[0].revalidate();
            badgeWrapper[0].repaint();
            urgencyContainer.revalidate();
            urgencyContainer.repaint();
        });

        // Safety Emergency Callout Banner (Immediate Hazards)
        JPanel emergencyBanner = new JPanel(new BorderLayout(Tokens.space(2), 0));
        emergencyBanner.setBackground(new Color(0xFE, 0xF2, 0xF2));
        emergencyBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xFE, 0xCA, 0xCA), 1, true),
                BorderFactory.createEmptyBorder(Tokens.space(2), Tokens.space(3), Tokens.space(2), Tokens.space(3))
        ));
        emergencyBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(55)));
        JLabel emergencyIcon = new JLabel(Icons.get("alert-triangle", 16, new Color(0xDC, 0x26, 0x26)));
        JLabel emergencyText = new JLabel("<html><span style='color: #991B1B; font-weight: bold;'>Immediate Safety Hazard?</span> <span style='color: #7F1D1D;'>For active fires, gas leaks, physical threats, or urgent medical emergencies, immediately contact Hostel Warden Security at <b>ext. 2222</b> in addition to lodging this ticket.</span></html>");
        emergencyText.setFont(Fonts.xs());
        emergencyBanner.add(emergencyIcon, BorderLayout.WEST);
        emergencyBanner.add(emergencyText, BorderLayout.CENTER);

        // 5. Description Text Area
        JLabel descLbl = new JLabel("DETAILED DESCRIPTION (20 to 2000 characters)");
        descLbl.setFont(Fonts.xs());
        descLbl.setForeground(Tokens.textMuted());

        JTextArea descArea = new JTextArea(5, 40);
        descArea.setFont(Fonts.body());
        descArea.setBackground(Tokens.surface());
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(10), UIScale.scale(8), UIScale.scale(10)));
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setBorder(BorderFactory.createLineBorder(Tokens.border(), 1, true));
        descScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(130)));

        formContent.add(titleLbl);
        formContent.add(Box.createVerticalStrut(Tokens.space(1)));
        formContent.add(titleField);
        formContent.add(Box.createVerticalStrut(Tokens.space(3)));

        formContent.add(catLbl);
        formContent.add(Box.createVerticalStrut(Tokens.space(1)));
        formContent.add(categoryCombo);
        formContent.add(Box.createVerticalStrut(Tokens.space(3)));

        formContent.add(locLbl);
        formContent.add(Box.createVerticalStrut(Tokens.space(1)));
        formContent.add(locField);
        formContent.add(Box.createVerticalStrut(Tokens.space(3)));

        formContent.add(urgLbl);
        formContent.add(Box.createVerticalStrut(Tokens.space(1)));
        formContent.add(urgencyContainer);
        formContent.add(Box.createVerticalStrut(Tokens.space(3)));

        formContent.add(emergencyBanner);
        formContent.add(Box.createVerticalStrut(Tokens.space(3)));

        formContent.add(descLbl);
        formContent.add(Box.createVerticalStrut(Tokens.space(1)));
        formContent.add(descScroll);
        formContent.add(Box.createVerticalStrut(Tokens.space(4)));

        // Action Buttons Row
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.space(2), 0));
        actionRow.setOpaque(false);

        AppButton cancelBtn = new AppButton("Cancel", AppButton.Variant.SECONDARY, AppButton.Size.MD);
        cancelBtn.addActionListener(e -> {
            activeSidebarItem = "My Complaints";
            mainCardLayout.show(mainViewContainer, "My Complaints");
            refreshSidebar();
        });

        AppButton submitBtn = new AppButton("Submit Complaint", AppButton.Variant.PRIMARY, AppButton.Size.MD);
        submitBtn.addActionListener(e -> {
            String title = titleField.getText();
            Category selectedCat = (Category) categoryCombo.getSelectedItem();
            String location = locField.getText();
            String desc = descArea.getText();

            errorBox.setVisible(false);

            if (title == null || title.trim().length() < 5 || title.trim().length() > 150) {
                formError.setMessage("Complaint title must be between 5 and 150 characters.");
                errorBox.setVisible(true);
                return;
            }
            if (selectedCat == null) {
                formError.setMessage("Please select a complaint category.");
                errorBox.setVisible(true);
                return;
            }
            if (desc == null || desc.trim().length() < 20 || desc.trim().length() > 2000) {
                formError.setMessage("Complaint description must be between 20 and 2000 characters.");
                errorBox.setVisible(true);
                return;
            }

            try {
                complaintService.createComplaint(title, desc, selectedCat.categoryId(), location);
                JOptionPane.showMessageDialog(
                        mainFrame,
                        "Your complaint has been successfully lodged and assigned a ticket ID.",
                        "Complaint Lodged",
                        JOptionPane.INFORMATION_MESSAGE
                );
                titleField.setText("");
                locField.setText("");
                descArea.setText("");
                pollNotifications(false);
                refreshViewsForRole(activeUser);
                activeSidebarItem = "My Complaints";
                mainCardLayout.show(mainViewContainer, "My Complaints");
                refreshSidebar();
            } catch (Exception ex) {
                formError.setMessage(ex.getMessage());
                errorBox.setVisible(true);
            }
        });

        actionRow.add(cancelBtn);
        actionRow.add(submitBtn);
        formContent.add(actionRow);

        card.add(formContent, BorderLayout.CENTER);
        container.add(card);

        scrollContent.add(container, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(true);
        scrollPane.setBackground(Tokens.bg());
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(Tokens.bg());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        return scrollPane;
    }

    // ==========================================================================
    // COMPONENT: WELCOME HERO PANEL (ADMIN)
    // ==========================================================================

    private static Card createWelcomeHeroBanner() {
        Card card = new Card(new BorderLayout(Tokens.space(3), 0), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(5), Tokens.space(4), Tokens.space(5)));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Tokens.space(3)));

        JPanel topBadgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), 0));
        topBadgeRow.setOpaque(false);

        JLabel statusPill = new JLabel("●  Hostel Block Desk Active");
        statusPill.setFont(Fonts.xs());
        statusPill.setForeground(Tokens.forestGreen());
        statusPill.setBackground(Tokens.softSage());
        statusPill.setOpaque(true);
        statusPill.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JLabel termLabel = new JLabel("Term Fall '25");
        termLabel.setFont(Fonts.xs());
        termLabel.setForeground(Tokens.textMuted());

        topBadgeRow.add(statusPill);
        topBadgeRow.add(termLabel);

        String greeting = "Good " + getTimeOfDayGreeting() + (activeUser != null ? ", " + activeUser.fullName() : "") + ". Let's get things sorted.";
        JLabel headline = new JLabel(greeting);
        headline.setFont(Fonts.h1());
        headline.setForeground(Tokens.text());

        JLabel subtext = new JLabel("<html><body style='width: 100%; color: #66685F;'>"
                + "Report hostel issues, track progress, and know who's accountable across dining halls and living blocks."
                + "</body></html>");
        subtext.setFont(Fonts.body());

        left.add(topBadgeRow);
        left.add(Box.createVerticalStrut(Tokens.space(2)));
        left.add(headline);
        left.add(Box.createVerticalStrut(Tokens.space(1)));
        left.add(subtext);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.space(2), 0));
        rightButtons.setOpaque(false);
        rightButtons.setBorder(BorderFactory.createEmptyBorder(Tokens.space(2), 0, 0, 0));

        AppButton searchBtn = new AppButton("Search", AppButton.Variant.SECONDARY, AppButton.Size.SM);
        searchBtn.setIcon(Icons.get("search", 13, Tokens.text()));

        AppButton refreshBtn = new AppButton("Refresh", AppButton.Variant.SECONDARY, AppButton.Size.SM);
        refreshBtn.setIcon(Icons.get("refresh-cw", 13, Tokens.text()));
        refreshBtn.addActionListener(e -> {
            if (activeUser != null) refreshViewsForRole(activeUser);
        });

        AppButton queueBtn = new AppButton("View Queue →", AppButton.Variant.PRIMARY, AppButton.Size.SM);
        queueBtn.addActionListener(e -> {
            activeSidebarItem = "Active Issues";
            mainCardLayout.show(mainViewContainer, "Active Issues");
            refreshSidebar();
        });

        rightButtons.add(searchBtn);
        rightButtons.add(refreshBtn);
        rightButtons.add(queueBtn);

        card.add(left, BorderLayout.CENTER);
        card.add(rightButtons, BorderLayout.EAST);
        return card;
    }

    // ==========================================================================
    // COMPONENT: FOUR KPI CARDS (ADMIN — LIVE DATABASE BACKED)
    // ==========================================================================

    private static JPanel createKpiCardsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, Tokens.space(3), 0));
        row.setOpaque(false);

        boolean canConfidential = activeUser != null && activeUser.canViewConfidential();
        KpiSummary kpi;
        try {
            kpi = analyticsDao.getKpiSummary(canConfidential);
        } catch (Exception e) {
            log.warn("Could not query KPI summary: {}", e.getMessage());
            kpi = new KpiSummary(0, 0, 0, 0);
        }

        // 1. PENDING VERIFICATION
        row.add(createKpiCard(
                "PENDING VERIFICATION",
                "hourglass",
                new Color(0xD9, 0x77, 0x06), // amber
                new Color(0xFE, 0xF3, 0xC7), // soft amber circle
                String.valueOf(kpi.pending()),
                "Queue Active",
                new Color(0xB4, 0x53, 0x09),
                "Awaiting assignment"
        ));

        // 2. IN PROGRESS
        row.add(createKpiCard(
                "IN PROGRESS",
                "wrench",
                Tokens.forestGreen(),
                Tokens.softSage(),
                String.valueOf(kpi.inProgress()),
                "Dispatched",
                Tokens.forestGreen(),
                "Active maintenance & mess crew"
        ));

        // 3. RESOLVED
        row.add(createKpiCard(
                "RESOLVED",
                "check",
                Tokens.forestGreen(),
                Tokens.softSage(),
                String.valueOf(kpi.resolved()),
                "Closed Out",
                Tokens.forestGreen(),
                "Verified resolution notes"
        ));

        // 4. TOTAL COMPLAINTS
        row.add(createKpiCard(
                "TOTAL COMPLAINTS",
                "sliders",
                Color.WHITE,
                Tokens.forestGreen(),
                String.valueOf(kpi.total()),
                "System Total",
                Tokens.text(),
                "Across all categories"
        ));

        return row;
    }

    private static Card createKpiCard(String label, String iconName, Color iconColor, Color iconBg, String number, String subLabel, Color subLabelColor, String footerText) {
        Card card = new Card(new BorderLayout(), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createEmptyBorder(Tokens.space(3), Tokens.space(4), Tokens.space(3), Tokens.space(4)));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JLabel labelLbl = new JLabel(label);
        labelLbl.setFont(Fonts.xs());
        labelLbl.setForeground(Tokens.textMuted());

        JPanel iconBadge = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(iconBg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(UIScale.scale(24), UIScale.scale(24));
            }
        };
        iconBadge.setLayout(new GridBagLayout());
        iconBadge.setOpaque(false);
        JLabel icon = new JLabel(Icons.get(iconName, 12, iconColor));
        iconBadge.add(icon);

        topRow.add(labelLbl, BorderLayout.WEST);
        topRow.add(iconBadge, BorderLayout.EAST);

        JPanel numRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), 0));
        numRow.setOpaque(false);

        JLabel numLbl = new JLabel(number);
        numLbl.setFont(Fonts.monoBold(24));
        numLbl.setForeground(Tokens.text());

        JLabel subLbl = new JLabel(subLabel);
        subLbl.setFont(Fonts.smMedium());
        subLbl.setForeground(subLabelColor);

        numRow.add(numLbl);
        numRow.add(subLbl);

        JLabel footLbl = new JLabel(footerText);
        footLbl.setFont(Fonts.xs());
        footLbl.setForeground(Tokens.textMuted());

        content.add(topRow);
        content.add(Box.createVerticalStrut(Tokens.space(2)));
        content.add(numRow);
        content.add(Box.createVerticalStrut(Tokens.space(1)));
        content.add(footLbl);

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    // ==========================================================================
    // COMPONENT: RECENT COMPLAINTS ACTIVITY TABLE CARD (LIVE DATA)
    // ==========================================================================

    private static Card createRecentComplaintsTableCard(boolean isCompactPreview) {
        Card card = new Card(new BorderLayout(0, Tokens.space(3)), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(5), Tokens.space(4), Tokens.space(5)));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), 0));
        leftHeader.setOpaque(false);

        JLabel title = new JLabel("●  Recent Complaints Activity");
        title.setFont(Fonts.h2());
        title.setForeground(Tokens.text());

        int pageSize = isCompactPreview ? 5 : 20;
        Page<Complaint> page;
        try {
            ComplaintFilter filter = new ComplaintFilter(
                null,
                activeFilterStatus,
                activeFilterCategoryId,
                activeFilterPriority,
                null,
                null,
                0,
                pageSize,
                "urgency",
                true
            );
            page = complaintService.listComplaints(filter);
        } catch (Exception e) {
            log.warn("Failed to load complaints: {}", e.getMessage());
            page = Page.empty(0, pageSize);
        }

        String filterSuffix = (activeFilterCategoryId != null || activeFilterPriority != null || activeFilterStatus != null) ? " (Filtered)" : "";
        JLabel countBadge = new JLabel("Showing " + page.items().size() + " of " + page.totalItems() + filterSuffix);
        countBadge.setFont(Fonts.xs());
        countBadge.setForeground(Tokens.textMuted());
        countBadge.setBackground(Tokens.bgSubtle());
        countBadge.setOpaque(true);
        countBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        leftHeader.add(title);
        leftHeader.add(countBadge);

        JPanel rightFilters = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.space(2), 0));
        rightFilters.setOpaque(false);

        String catLabel = "All Categories ▾";
        if (activeFilterCategoryId != null) {
            try {
                catLabel = categoryDao.findById(activeFilterCategoryId).map(c -> c.categoryName() + " ▾").orElse("Category ▾");
            } catch (Exception ignored) {}
        }
        String prioLabel = activeFilterPriority != null ? "Priority: " + activeFilterPriority.dbValue() + " ▾" : "Priority: All ▾";
        String statusLabel = activeFilterStatus != null ? "Status: " + activeFilterStatus.dbValue() + " ▾" : "Status: Active ▾";

        JPanel catPill = createFilterDropdownPill(catLabel);
        JPanel prioPill = createFilterDropdownPill(prioLabel);
        JPanel statusPill = createFilterDropdownPill(statusLabel);

        catPill.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { showFilterDialog(); } });
        prioPill.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { showFilterDialog(); } });
        statusPill.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { showFilterDialog(); } });

        rightFilters.add(catPill);
        rightFilters.add(prioPill);
        rightFilters.add(statusPill);

        JButton refreshBtn = createIconButton("refresh-cw", 12);
        refreshBtn.setBorder(BorderFactory.createLineBorder(Tokens.border(), 1, true));
        refreshBtn.addActionListener(e -> {
            if (activeUser != null) refreshViewsForRole(activeUser);
        });
        rightFilters.add(refreshBtn);

        header.add(leftHeader, BorderLayout.WEST);
        header.add(rightFilters, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel tablePanel = new JPanel();
        tablePanel.setOpaque(false);
        tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.Y_AXIS));

        tablePanel.add(createTableHeaderRow());

        if (page.items().isEmpty()) {
            EmptyState emptyState = new EmptyState(
                    "No Active Complaints",
                    "All clear",
                    "There are currently no tickets matching your view."
            );
            tablePanel.add(emptyState);
        } else {
            for (Complaint c : page.items()) {
                tablePanel.add(createTableDataRow(c, true));
            }
        }

        card.add(tablePanel, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(Tokens.space(3), 0, 0, 0));

        JLabel footnote = new JLabel("✓  All tickets time-stamped and mapped to duty shift records.");
        footnote.setFont(Fonts.xs());
        footnote.setForeground(Tokens.textMuted());

        JPanel pagination = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.space(2), 0));
        pagination.setOpaque(false);

        JLabel prev = new JLabel("← Previous");
        prev.setFont(Fonts.xs());
        prev.setForeground(Tokens.textMuted());

        JLabel p1 = new JLabel(" 1 ");
        p1.setFont(Fonts.xs());
        p1.setForeground(Color.WHITE);
        p1.setBackground(Tokens.forestGreen());
        p1.setOpaque(true);
        p1.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));

        JLabel next = new JLabel("Next →");
        next.setFont(Fonts.xs());
        next.setForeground(Tokens.textMuted());

        pagination.add(prev);
        pagination.add(p1);
        pagination.add(next);

        footer.add(footnote, BorderLayout.WEST);
        footer.add(pagination, BorderLayout.EAST);

        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private static JPanel createFilterDropdownPill(String text) {
        JPanel pill = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(1), 0));
        pill.setBackground(Tokens.bgSubtle());
        pill.setOpaque(true);
        pill.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(UIScale.scale(3), UIScale.scale(8), UIScale.scale(3), UIScale.scale(8))
        ));
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel lbl = new JLabel(text);
        lbl.setFont(Fonts.xs());
        lbl.setForeground(Tokens.text());

        pill.add(lbl);
        return pill;
    }

    private static void addTableColumn(JPanel panel, Component comp, int gridx, double weightx, int prefWidth, int anchor) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = gridx;
        c.gridy = 0;
        c.weightx = weightx;
        c.weighty = 1.0;
        c.fill = (weightx > 0.0) ? GridBagConstraints.BOTH : GridBagConstraints.VERTICAL;
        c.anchor = anchor;
        c.insets = new Insets(0, UIScale.scale(4), 0, UIScale.scale(4));
        if (prefWidth > 0 && weightx == 0.0) {
            Dimension d = new Dimension(prefWidth, comp.getPreferredSize().height);
            comp.setPreferredSize(d);
            comp.setMinimumSize(d);
        }
        panel.add(comp, c);
    }

    private static JPanel createTableHeaderRow() {
        JPanel header = new JPanel(new GridBagLayout());
        header.setBackground(Tokens.bgSubtle());
        header.setOpaque(true);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.border()),
                BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(10), UIScale.scale(8), UIScale.scale(10))
        ));

        addTableColumn(header, createHeaderCell("TICKET ID"), 0, 0.0, UIScale.scale(95), GridBagConstraints.WEST);
        addTableColumn(header, createHeaderCell("ISSUE DESCRIPTION"), 1, 1.0, 0, GridBagConstraints.WEST);
        addTableColumn(header, createHeaderCell("LOCATION"), 2, 0.0, UIScale.scale(180), GridBagConstraints.WEST);
        addTableColumn(header, createHeaderCell("ASSIGNED / REPORTER"), 3, 0.0, UIScale.scale(160), GridBagConstraints.WEST);
        addTableColumn(header, createHeaderCell("PRIORITY"), 4, 0.0, UIScale.scale(90), GridBagConstraints.WEST);
        addTableColumn(header, createHeaderCell("STATUS"), 5, 0.0, UIScale.scale(110), GridBagConstraints.WEST);
        addTableColumn(header, createHeaderCell("ACTION"), 6, 0.0, UIScale.scale(85), GridBagConstraints.CENTER);
        return header;
    }

    private static JLabel createHeaderCell(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Fonts.xs());
        l.setForeground(Tokens.textMuted());
        return l;
    }

    private static JPanel createTableDataRow(Complaint c, boolean isAdmin) {
        String ticketId = "#TKT-" + c.complaintId();
        String title = c.title();
        String category = (c.isConfidential() ? "🔒 " : "● ") + (c.categoryName() != null ? c.categoryName() : "General");
        Color categoryColor = c.isConfidential() ? Tokens.confFg() : Tokens.forestGreen();
        String location = c.location() != null ? c.location() : "Hostel Premises";

        String personName = isAdmin
                ? (c.studentName() != null ? c.studentName() : "Student #" + c.studentId())
                : (c.assignedAdminName() != null ? c.assignedAdminName() : "Pending Assignment");
        String personRole = isAdmin ? "Resident" : "Duty Staff";
        String initials = computeInitials(personName);
        Color avatarBg = new Color(0xCC, 0xFB, 0xF1);

        String priority = c.priority() != null ? c.priority().dbValue()
                : (c.studentUrgency() != null ? c.studentUrgency().dbValue() : "Normal");
        String status = c.status() != null ? c.status().dbValue() : "Pending";
        String actionLabel = isAdmin ? "Inspect" : "View";
        boolean isUrgent = c.priority() == Priority.HIGH || c.studentUrgency() == Urgency.HIGH;

        return createTableDataRow(
                ticketId,
                title,
                category,
                categoryColor,
                location,
                initials,
                avatarBg,
                personName,
                personRole,
                priority,
                status,
                actionLabel,
                isUrgent,
                () -> showComplaintDetailsDialog(c)
        );
    }

    private static JPanel createTableDataRow(
            String ticketId,
            String title,
            String category,
            Color categoryColor,
            String location,
            String avatarInitials,
            Color avatarBg,
            String staffName,
            String staffRole,
            String priority,
            String status,
            String actionLabel,
            boolean isUrgentAction,
            Runnable onAction
    ) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(Tokens.surface()); // #FFFEF0
        row.setOpaque(true);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.border()),
                BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(10), UIScale.scale(8), UIScale.scale(10))
        ));

        // 1. Ticket ID
        JLabel idLbl = new JLabel(ticketId);
        idLbl.setFont(Fonts.monoBold(13));
        idLbl.setForeground(Tokens.text());

        // 2. Issue Description + Category Subtitle
        JPanel descPanel = new JPanel();
        descPanel.setOpaque(false);
        descPanel.setLayout(new BoxLayout(descPanel, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(Fonts.smMedium());
        titleLbl.setForeground(Tokens.text());

        JLabel catLbl = new JLabel(category);
        catLbl.setFont(Fonts.xs());
        catLbl.setForeground(categoryColor);

        descPanel.add(titleLbl);
        descPanel.add(catLbl);

        // 3. Location (with map-pin icon)
        JPanel locPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(1), 0));
        locPanel.setOpaque(false);
        JLabel pinIcon = new JLabel(Icons.get("map-pin", 12, Tokens.textMuted()));
        JLabel locLbl = new JLabel(location);
        locLbl.setFont(Fonts.sm());
        locLbl.setForeground(Tokens.text());
        locPanel.add(pinIcon);
        locPanel.add(locLbl);

        // 4. Assigned Staff or Reporter
        JPanel staffPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), 0));
        staffPanel.setOpaque(false);

        JPanel staffAvatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(avatarBg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Tokens.text());
                g2.setFont(Fonts.xs());
                g2.drawString(avatarInitials, UIScale.scale(4), UIScale.scale(14));
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(UIScale.scale(22), UIScale.scale(22));
            }
        };
        staffAvatar.setOpaque(false);

        JPanel staffText = new JPanel();
        staffText.setOpaque(false);
        staffText.setLayout(new BoxLayout(staffText, BoxLayout.Y_AXIS));

        JLabel sName = new JLabel(staffName);
        sName.setFont(Fonts.smMedium());
        sName.setForeground(Tokens.text());

        JLabel sRole = new JLabel(staffRole);
        sRole.setFont(Fonts.xs());
        sRole.setForeground(Tokens.textMuted());

        staffText.add(sName);
        staffText.add(sRole);

        staffPanel.add(staffAvatar);
        staffPanel.add(staffText);

        // 5. Priority Chip
        JPanel prioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        prioPanel.setOpaque(false);
        prioPanel.add(ChipLabel.priority(priority));

        // 6. Status Chip
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusPanel.setOpaque(false);
        statusPanel.add(ChipLabel.status(status));

        // 7. Action Button
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        actionPanel.setOpaque(false);

        JButton actionBtn = new JButton(actionLabel);
        actionBtn.setFont(Fonts.xs());
        actionBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        actionBtn.setFocusPainted(false);

        if (isUrgentAction) {
            actionBtn.setBackground(new Color(0xD9, 0x53, 0x4F));
            actionBtn.setForeground(Color.WHITE);
            actionBtn.setOpaque(true);
            actionBtn.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(12), UIScale.scale(4), UIScale.scale(12)));
        } else {
            actionBtn.setBackground(Tokens.surface());
            actionBtn.setForeground(Tokens.text());
            actionBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Tokens.border(), 1, true),
                    BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(12), UIScale.scale(4), UIScale.scale(12))
            ));
        }

        actionBtn.addActionListener(e -> {
            if (onAction != null) {
                onAction.run();
            }
        });

        actionPanel.add(actionBtn);

        addTableColumn(row, idLbl, 0, 0.0, UIScale.scale(95), GridBagConstraints.WEST);
        addTableColumn(row, descPanel, 1, 1.0, 0, GridBagConstraints.WEST);
        addTableColumn(row, locPanel, 2, 0.0, UIScale.scale(180), GridBagConstraints.WEST);
        addTableColumn(row, staffPanel, 3, 0.0, UIScale.scale(160), GridBagConstraints.WEST);
        addTableColumn(row, prioPanel, 4, 0.0, UIScale.scale(90), GridBagConstraints.WEST);
        addTableColumn(row, statusPanel, 5, 0.0, UIScale.scale(110), GridBagConstraints.WEST);
        addTableColumn(row, actionPanel, 6, 0.0, UIScale.scale(85), GridBagConstraints.CENTER);
        return row;
    }

    // ==========================================================================
    // SCREEN C — THREE-CARD ANALYTICS & PRIVACY DECK
    // ==========================================================================

    private static JPanel createAnalyticsAndPrivacyDeck() {
        JPanel deck = new JPanel(new GridLayout(1, 3, Tokens.space(4), 0));
        deck.setOpaque(false);

        // Card 1: Category Distribution with live database counts
        deck.add(createCategoryDistributionCard());

        // Card 2: Resolution SLA Metrics with 2x2 metric tiles
        deck.add(createResolutionSlaMetricsCard());

        // Card 3: Resident Privacy & Fair Redressal (Student Charter)
        deck.add(createResidentPrivacyCard());

        return deck;
    }

    // Card 1: Category Distribution (Database Backed)
    private static Card createCategoryDistributionCard() {
        Card card = new Card(new BorderLayout(0, Tokens.space(3)), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(4), Tokens.space(4)));

        boolean canConfidential = activeUser != null && activeUser.canViewConfidential();
        List<CategoryCount> catCounts;
        try {
            catCounts = analyticsDao.getCategoryDistribution(canConfidential);
        } catch (Exception e) {
            log.warn("Could not load category distribution: {}", e.getMessage());
            catCounts = List.of();
        }

        long totalInCats = catCounts.stream().mapToLong(CategoryCount::count).sum();

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("🌐  Category Distribution");
        title.setFont(Fonts.h3());
        title.setForeground(Tokens.text());

        JLabel countBadge = new JLabel(totalInCats + " total");
        countBadge.setFont(Fonts.xs());
        countBadge.setForeground(Tokens.textMuted());

        header.add(title, BorderLayout.WEST);
        header.add(countBadge, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        // Progress bars
        JPanel barsPanel = new JPanel();
        barsPanel.setOpaque(false);
        barsPanel.setLayout(new BoxLayout(barsPanel, BoxLayout.Y_AXIS));

        Color[] barColors = {
                Tokens.forestGreen(),
                new Color(0x0A, 0x6E, 0x55),
                new Color(0x4A, 0x9D, 0x82),
                new Color(0xD9, 0x77, 0x06),
                new Color(0x02, 0x84, 0xC7)
        };

        if (catCounts.isEmpty()) {
            JLabel emptyLbl = new JLabel("No category complaint records found.");
            emptyLbl.setFont(Fonts.sm());
            emptyLbl.setForeground(Tokens.textMuted());
            barsPanel.add(emptyLbl);
        } else {
            int colorIdx = 0;
            for (CategoryCount cc : catCounts) {
                int pct = totalInCats > 0 ? (int) Math.round((cc.count() * 100.0) / totalInCats) : 0;
                Color c = barColors[colorIdx % barColors.length];
                colorIdx++;
                barsPanel.add(createCategoryProgressBar(cc.categoryName(), pct + "% (" + cc.count() + " tickets)", pct, c));
                barsPanel.add(Box.createVerticalStrut(Tokens.space(2)));
            }
        }

        card.add(barsPanel, BorderLayout.CENTER);

        // Footer Link
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(Tokens.space(3), 0, 0, 0));

        JLabel note = new JLabel("Dining and safety issues prioritized for dispatch");
        note.setFont(Fonts.xs());
        note.setForeground(Tokens.textMuted());

        JLabel link = new JLabel("View Active Issues →");
        link.setFont(Fonts.xs());
        link.setForeground(Tokens.forestGreen());
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                activeSidebarItem = "Active Issues";
                mainCardLayout.show(mainViewContainer, "Active Issues");
                refreshSidebar();
            }
        });

        footer.add(note, BorderLayout.WEST);
        footer.add(link, BorderLayout.EAST);

        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private static JPanel createCategoryProgressBar(String label, String stat, int percentage, Color barColor) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JPanel textRow = new JPanel(new BorderLayout());
        textRow.setOpaque(false);

        JLabel nameLbl = new JLabel(label);
        nameLbl.setFont(Fonts.sm());
        nameLbl.setForeground(Tokens.text());

        JLabel statLbl = new JLabel(stat);
        statLbl.setFont(Fonts.xs());
        statLbl.setForeground(Tokens.textMuted());

        textRow.add(nameLbl, BorderLayout.WEST);
        textRow.add(statLbl, BorderLayout.EAST);

        JPanel bar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(Tokens.bgSubtle());
                g2.fillRoundRect(0, 0, w, h, h, h);
                g2.setColor(barColor);
                int fillW = Math.max(h, (w * percentage) / 100);
                g2.fillRoundRect(0, 0, fillW, h, h, h);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(UIScale.scale(100), UIScale.scale(8));
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, UIScale.scale(8));
            }
        };
        bar.setOpaque(false);

        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(36)));
        p.add(textRow);
        p.add(Box.createVerticalStrut(Tokens.space(1)));
        p.add(bar);
        return p;
    }

    // Card 2: Resolution SLA Metrics
    private static Card createResolutionSlaMetricsCard() {
        Card card = new Card(new BorderLayout(0, Tokens.space(3)), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(4), Tokens.space(4)));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("⏱  Resolution SLA Metrics");
        title.setFont(Fonts.h3());
        title.setForeground(Tokens.text());

        JLabel complianceBadge = new JLabel("High Compliance");
        complianceBadge.setFont(Fonts.xs());
        complianceBadge.setForeground(Tokens.forestGreen());
        complianceBadge.setBackground(Tokens.softSage());
        complianceBadge.setOpaque(true);
        complianceBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        header.add(title, BorderLayout.WEST);
        header.add(complianceBadge, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, Tokens.space(2), Tokens.space(2)));
        grid.setOpaque(false);

        grid.add(createMetricTile("First Acknowledged", "42m", "↓ 8m vs last week", Tokens.forestGreen()));
        grid.add(createMetricTile("24h SLA Adherence", "94.2%", "Target: >90%", Tokens.forestGreen()));
        grid.add(createMetricTile("Escalation Rate", "2.8%", "Superintendent audit", Tokens.textMuted()));
        grid.add(createMetricTile("Satisfaction Score", "4.7 / 5.0", "188 student reviews", Tokens.textMuted()));

        card.add(grid, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(Tokens.space(3), 0, 0, 0));

        JLabel note = new JLabel("Wardens notified on tickets >12h");
        note.setFont(Fonts.xs());
        note.setForeground(Tokens.textMuted());

        JLabel link = new JLabel("Audit Telemetry Active");
        link.setFont(Fonts.xs());
        link.setForeground(Tokens.forestGreen());

        footer.add(note, BorderLayout.WEST);
        footer.add(link, BorderLayout.EAST);

        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private static JPanel createMetricTile(String label, String value, String subtext, Color subColor) {
        JPanel tile = new JPanel();
        tile.setBackground(Tokens.bgSubtle());
        tile.setOpaque(true);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(Tokens.space(2), Tokens.space(3), Tokens.space(2), Tokens.space(3))
        ));
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));

        JLabel l = new JLabel(label);
        l.setFont(Fonts.xs());
        l.setForeground(Tokens.textMuted());

        JLabel v = new JLabel(value);
        v.setFont(Fonts.monoBold(20));
        v.setForeground(Tokens.text());

        JLabel s = new JLabel(subtext);
        s.setFont(Fonts.xs());
        s.setForeground(subColor);

        tile.add(l);
        tile.add(Box.createVerticalStrut(Tokens.space(1)));
        tile.add(v);
        tile.add(Box.createVerticalStrut(Tokens.space(1)));
        tile.add(s);
        return tile;
    }

    // Card 3: Resident Privacy & Fair Redressal (Student Charter)
    private static Card createResidentPrivacyCard() {
        Card card = new Card(new BorderLayout(0, Tokens.space(3)), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(4), Tokens.space(4)));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(3), 0));
        header.setOpaque(false);

        JPanel shieldBadge = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Tokens.forestGreen());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), UIScale.scale(8), UIScale.scale(8));
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(UIScale.scale(32), UIScale.scale(32));
            }
        };
        shieldBadge.setLayout(new GridBagLayout());
        shieldBadge.setOpaque(false);
        shieldBadge.add(new JLabel(Icons.get("shield", 16, Color.WHITE)));

        JPanel titleText = new JPanel();
        titleText.setOpaque(false);
        titleText.setLayout(new BoxLayout(titleText, BoxLayout.Y_AXIS));

        JLabel tag = new JLabel("STUDENT CHARTER");
        tag.setFont(Fonts.xs());
        tag.setForeground(Tokens.textMuted());

        JLabel title = new JLabel("Resident Privacy & Fair Redressal");
        title.setFont(Fonts.h3());
        title.setForeground(Tokens.text());

        titleText.add(tag);
        titleText.add(title);

        header.add(shieldBadge);
        header.add(titleText);
        card.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel desc = new JLabel(
                "<html><p style='line-height: 1.5; color: #66685F; font-size: 13px;'>"
                        + "All student reports can be lodged anonymously or with protected identity. "
                        + "Wardens and mess contractors see room-level details solely for maintenance dispatch. "
                        + "Retaliation safeguards strictly enforced under Campus Living Bylaw 4.2."
                        + "</p></html>"
        );
        desc.setFont(Fonts.body());

        JPanel lockBox = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), Tokens.space(2)));
        lockBox.setBackground(Tokens.bgSubtle());
        lockBox.setOpaque(true);
        lockBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));

        JLabel lockIcon = new JLabel(Icons.get("lock", 14, Tokens.forestGreen()));
        JLabel lockText = new JLabel("End-to-End Encrypted Token Hashing Active");
        lockText.setFont(Fonts.xs());
        lockText.setForeground(Tokens.text());

        lockBox.add(lockIcon);
        lockBox.add(lockText);

        body.add(desc);
        body.add(Box.createVerticalStrut(Tokens.space(3)));
        body.add(lockBox);
        card.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(Tokens.space(3), 0, 0, 0));

        JLabel note = new JLabel("Bylaw 4.2 Protected");
        note.setFont(Fonts.xs());
        note.setForeground(Tokens.textMuted());

        JLabel checkIcon = new JLabel(Icons.get("check", 14, Tokens.forestGreen()));

        footer.add(note, BorderLayout.WEST);
        footer.add(checkIcon, BorderLayout.EAST);

        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    // ==========================================================================
    // COMPLAINT DETAILS MODAL DIALOG
    // ==========================================================================

    private static void showComplaintDetailsDialog(Complaint complaint) {
        if (complaint == null) return;

        Complaint fresh;
        try {
            fresh = complaintService.getComplaint(complaint.complaintId());
        } catch (Exception e) {
            fresh = complaint;
        }

        JDialog dialog = new JDialog(mainFrame, "Ticket Details — #TKT-" + fresh.complaintId(), true);
        dialog.getContentPane().setBackground(Tokens.bg());
        dialog.setSize(UIScale.scale(560), UIScale.scale(620));
        dialog.setLocationRelativeTo(mainFrame);

        JPanel content = new JPanel();
        content.setBackground(Tokens.bg());
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(4), Tokens.space(4)));

        Card card = new Card(new BorderLayout(), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(5), Tokens.space(4), Tokens.space(5))
        ));

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        // Header chips row
        JPanel topChips = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), 0));
        topChips.setOpaque(false);

        JLabel idChip = new JLabel("#TKT-" + fresh.complaintId());
        idChip.setFont(Fonts.monoBold(13));
        idChip.setForeground(Tokens.text());

        topChips.add(idChip);
        topChips.add(ChipLabel.status(fresh.status() != null ? fresh.status().dbValue() : "Pending"));
        if (fresh.priority() != null) {
            topChips.add(ChipLabel.priority(fresh.priority().dbValue()));
        } else if (fresh.studentUrgency() != null) {
            topChips.add(ChipLabel.priority(fresh.studentUrgency().dbValue()));
        }
        if (fresh.isConfidential()) {
            JLabel conf = new JLabel("🔒 Confidential");
            conf.setFont(Fonts.xs());
            conf.setForeground(Tokens.confFg());
            conf.setBackground(Tokens.confBg());
            conf.setOpaque(true);
            conf.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            topChips.add(conf);
        }

        inner.add(topChips);
        inner.add(Box.createVerticalStrut(Tokens.space(2)));

        // Title
        JLabel titleLbl = new JLabel("<html><body style='width: 440px;'><b>" + fresh.title() + "</b></body></html>");
        titleLbl.setFont(Fonts.h2());
        titleLbl.setForeground(Tokens.text());
        inner.add(titleLbl);
        inner.add(Box.createVerticalStrut(Tokens.space(3)));

        // Key-Value Grid
        JPanel infoGrid = new JPanel(new GridLayout(3, 2, Tokens.space(2), Tokens.space(1)));
        infoGrid.setOpaque(false);

        infoGrid.add(createInfoField("CATEGORY", fresh.categoryName() != null ? fresh.categoryName() : "General"));
        infoGrid.add(createInfoField("LOCATION", fresh.location() != null ? fresh.location() : "Not specified"));
        infoGrid.add(createInfoField("SUBMITTED BY", fresh.studentName() != null ? fresh.studentName() : "Student #" + fresh.studentId()));
        infoGrid.add(createInfoField("ASSIGNED STAFF", fresh.assignedAdminName() != null ? fresh.assignedAdminName() : "Unassigned"));
        infoGrid.add(createInfoField("FILED AT", fresh.createdAt() != null ? fresh.createdAt().toString() : "—"));
        infoGrid.add(createInfoField("RESOLVED AT", fresh.resolvedAt() != null ? fresh.resolvedAt().toString() : "Pending"));

        inner.add(infoGrid);
        inner.add(Box.createVerticalStrut(Tokens.space(3)));

        // Description Box
        JLabel descHeader = new JLabel("ISSUE DESCRIPTION");
        descHeader.setFont(Fonts.xs());
        descHeader.setForeground(Tokens.textMuted());
        inner.add(descHeader);
        inner.add(Box.createVerticalStrut(Tokens.space(1)));

        JTextArea descText = new JTextArea(fresh.description());
        descText.setFont(Fonts.body());
        descText.setLineWrap(true);
        descText.setWrapStyleWord(true);
        descText.setEditable(false);
        descText.setBackground(Tokens.surface2());
        descText.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JScrollPane descScroll = new JScrollPane(descText);
        descScroll.setBorder(BorderFactory.createLineBorder(Tokens.border(), 1, true));
        descScroll.setPreferredSize(new Dimension(UIScale.scale(460), UIScale.scale(80)));
        inner.add(descScroll);

        // Resolution Notes (if present)
        if (fresh.resolutionNotes() != null && !fresh.resolutionNotes().isBlank()) {
            inner.add(Box.createVerticalStrut(Tokens.space(2)));
            JLabel resHeader = new JLabel("RESOLUTION NOTES");
            resHeader.setFont(Fonts.xs());
            resHeader.setForeground(Tokens.forestGreen());
            inner.add(resHeader);
            inner.add(Box.createVerticalStrut(Tokens.space(1)));

            JTextArea resText = new JTextArea(fresh.resolutionNotes());
            resText.setFont(Fonts.body());
            resText.setLineWrap(true);
            resText.setWrapStyleWord(true);
            resText.setEditable(false);
            resText.setBackground(Tokens.softSage());
            resText.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            JScrollPane resScroll = new JScrollPane(resText);
            resScroll.setBorder(BorderFactory.createLineBorder(Tokens.border(), 1, true));
            resScroll.setPreferredSize(new Dimension(UIScale.scale(460), UIScale.scale(60)));
            inner.add(resScroll);
        }

        inner.add(Box.createVerticalStrut(Tokens.space(4)));

        // Action Buttons Row
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.space(2), 0));
        btnRow.setOpaque(false);

        // If user is Admin and ticket is active, allow transition
        if (activeUser != null && activeUser.isAdmin()) {
            final Complaint target = fresh;
            if (target.status() == Status.PENDING) {
                AppButton progressBtn = new AppButton("Mark In Progress", AppButton.Variant.PRIMARY, AppButton.Size.SM);
                progressBtn.addActionListener(e -> {
                    try {
                        complaintService.updateStatusAndPriority(
                                target.complaintId(),
                                Status.IN_PROGRESS,
                                target.priority(),
                                activeUser.userId(),
                                "Marked In Progress by " + activeUser.fullName(),
                                null,
                                target.updatedAt()
                        );
                        dialog.dispose();
                        pollNotifications(false);
                        refreshViewsForRole(activeUser);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Update Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
                btnRow.add(progressBtn);
            } else if (target.status() == Status.IN_PROGRESS) {
                AppButton resolveBtn = new AppButton("Resolve Issue...", AppButton.Variant.PRIMARY, AppButton.Size.SM);
                resolveBtn.addActionListener(e -> {
                    String notes = JOptionPane.showInputDialog(
                            dialog,
                            "Enter mandatory resolution notes:",
                            "Resolve Complaint #" + target.complaintId(),
                            JOptionPane.PLAIN_MESSAGE
                    );
                    if (notes != null && !notes.isBlank()) {
                        try {
                            complaintService.updateStatusAndPriority(
                                    target.complaintId(),
                                    Status.RESOLVED,
                                    target.priority(),
                                    activeUser.userId(),
                                    "Resolved by " + activeUser.fullName(),
                                    notes.trim(),
                                    target.updatedAt()
                            );
                            dialog.dispose();
                            pollNotifications(false);
                            refreshViewsForRole(activeUser);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Update Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });
                btnRow.add(resolveBtn);
            }
        }

        AppButton closeBtn = new AppButton("Close", AppButton.Variant.SECONDARY, AppButton.Size.SM);
        closeBtn.addActionListener(e -> dialog.dispose());
        btnRow.add(closeBtn);

        inner.add(btnRow);

        card.add(inner, BorderLayout.CENTER);
        content.add(card);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setVisible(true);
    }

    private static JPanel createInfoField(String label, String value) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel l = new JLabel(label);
        l.setFont(Fonts.xs());
        l.setForeground(Tokens.textMuted());

        JLabel v = new JLabel(value);
        v.setFont(Fonts.smMedium());
        v.setForeground(Tokens.text());

        p.add(l);
        p.add(v);
        return p;
    }

    private static String computeInitials(String name) {
        if (name == null || name.isBlank()) return "U";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private static String getTimeOfDayGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "morning";
        if (hour < 17) return "afternoon";
        return "evening";
    }

    // ==========================================================================
    // BOTTOM TELEMETRY STATUS BAR
    // ==========================================================================

    private static JPanel createBottomStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Tokens.bg()); // #FDFBD4
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Tokens.border()),
                BorderFactory.createEmptyBorder(Tokens.space(1), Tokens.space(4), Tokens.space(1), Tokens.space(4))
        ));
        bar.setPreferredSize(new Dimension(0, UIScale.scale(28)));
        bar.setMinimumSize(new Dimension(0, UIScale.scale(28)));

        bottomStatusLabel = new JLabel(dbConnected
                ? "<html><span style='color: #064E3B; font-weight: bold;'>●</span> Connected to MySQL (staysync_db) &nbsp;•&nbsp; Sync: Live &nbsp;•&nbsp; SLA: 98.4% On-Track</html>"
                : "<html><span style='color: #D97706; font-weight: bold;'>●</span> Checking database connection... &nbsp;•&nbsp; Standalone Preview Mode</html>");
        bottomStatusLabel.setFont(Fonts.xs());
        bottomStatusLabel.setForeground(Tokens.textMuted());

        JLabel rightStatus = new JLabel("StaySync • Premises & Mess Resolution Suite");
        rightStatus.setFont(Fonts.xs());
        rightStatus.setForeground(Tokens.textMuted());

        bar.add(bottomStatusLabel, BorderLayout.WEST);
        bar.add(rightStatus, BorderLayout.EAST);
        return bar;
    }

    /**
     * Executes database connectivity check and safe initialization asynchronously off the EDT on startup.
     */
    private static void runDatabaseStartupCheck() {
        Thread checkThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            boolean ok = false;
            String diagnosticMessage = null;
            try {
                // 1. Run safe pre-flight initialization & schema verification
                com.staysync.db.DatabaseInitializer.InitResult initResult =
                        com.staysync.db.DatabaseInitializer.initialize();

                if (initResult.isSuccess()) {
                    // 2. Validate connection pool ping
                    ok = DataSourceProvider.testConnection();
                    if (ok) {
                        new com.staysync.dao.UserDao().ensureDemoAccounts();
                        diagnosticMessage = initResult.getMessage();
                    } else {
                        diagnosticMessage = "Connection pool could not connect to staysync_db.";
                    }
                } else {
                    ok = false;
                    diagnosticMessage = initResult.getMessage();
                }
            } catch (Exception e) {
                log.warn("Database initialization/health check ping failed: {}", e.getMessage());
                diagnosticMessage = e.getMessage();
            }
            long elapsed = Math.max(1, System.currentTimeMillis() - start);
            final boolean connected = ok;
            final long pingMs = elapsed;
            final String finalMsg = diagnosticMessage;
            dbConnected = connected;
            SwingUtilities.invokeLater(() -> updateDatabaseStatusUI(connected, pingMs, finalMsg));
        }, "StaySync-DbHealthCheck");
        checkThread.setDaemon(true);
        checkThread.start();
    }

    /**
     * Updates UI telemetry status indicators on the EDT following the health check.
     */
    private static void updateDatabaseStatusUI(boolean connected, long pingMs) {
        updateDatabaseStatusUI(connected, pingMs, null);
    }

    private static void updateDatabaseStatusUI(boolean connected, long pingMs, String message) {
        if (bottomStatusLabel != null) {
            if (connected) {
                bottomStatusLabel.setText("<html><span style='color: #064E3B; font-weight: bold;'>●</span> Connected to MySQL (staysync_db) &nbsp;•&nbsp; Sync: Live &nbsp;•&nbsp; SLA: 98.4% On-Track</html>");
            } else {
                String detail = (message != null && !message.isBlank()) ? message : "MySQL Unreachable";
                bottomStatusLabel.setText("<html><span style='color: #D97706; font-weight: bold;'>●</span> Database Offline &nbsp;•&nbsp; " + detail + "</html>");
            }
        }
        if (sidebarMeshLabel != null) {
            if (connected) {
                sidebarMeshLabel.setText("<html><span style='color: #064E3B; font-weight: bold;'>●</span>  Campus Mesh V2</html>");
                sidebarMeshLabel.setForeground(Tokens.forestGreen());
            } else {
                sidebarMeshLabel.setText("<html><span style='color: #D97706; font-weight: bold;'>●</span>  Standalone Preview</html>");
                sidebarMeshLabel.setForeground(Tokens.textMuted());
            }
        }
        if (sidebarPingLabel != null) {
            if (connected) {
                sidebarPingLabel.setText(pingMs + "ms ping");
                sidebarPingLabel.setForeground(Tokens.forestGreen());
            } else {
                sidebarPingLabel.setText("Offline");
                sidebarPingLabel.setForeground(Tokens.textFaint());
            }
        }
        if (loginPanel != null) {
            loginPanel.updateDatabaseStatus(connected, pingMs, message);
        }
    }

    // ==========================================================================
    // NOTIFICATIONS & BACKGROUND POLLING (SWING-SAFE & OFF-EDT)
    // ==========================================================================

    private static void startNotificationPolling() {
        stopNotificationPolling();
        notificationPollingTimer = new javax.swing.Timer(10000, e -> pollNotifications(false));
        notificationPollingTimer.setRepeats(true);
        notificationPollingTimer.start();
        pollNotifications(true);
    }

    private static void stopNotificationPolling() {
        if (notificationPollingTimer != null) {
            notificationPollingTimer.stop();
            notificationPollingTimer = null;
        }
    }

    private static void pollNotifications(boolean isInitial) {
        if (activeUser == null) return;

        CompletableFuture.supplyAsync(() -> {
            try {
                int unread = notificationService.getUnreadCount();
                List<Notification> recent = notificationService.getMyNotifications(10);
                return new NotificationPollData(unread, recent);
            } catch (Exception ex) {
                log.warn("Notification polling encountered error: {}", ex.getMessage());
                return null;
            }
        }).thenAcceptAsync(data -> {
            if (data == null || activeUser == null) return;

            updateNotificationBadge(data.unreadCount());

            // 1. Establish baseline on initial poll or first session fetch
            if (!notificationPollingInitialized || isInitial) {
                long maxId = 0L;
                for (Notification n : data.recent()) {
                    if (n.notificationId() > maxId) {
                        maxId = n.notificationId();
                    }
                }
                lastSeenNotificationId = maxId;
                notificationPollingInitialized = true;
                log.info("Notification polling baseline established for [{}] (baselineId: {}, unreadCount: {})",
                    activeUser.email(), lastSeenNotificationId, data.unreadCount());
                return;
            }

            // 2. Incremental polling: detect newly arrived notifications
            List<Notification> newNotifs = new ArrayList<>();
            long maxId = lastSeenNotificationId;
            for (Notification n : data.recent()) {
                if (n.notificationId() > lastSeenNotificationId) {
                    newNotifs.add(n);
                    if (n.notificationId() > maxId) {
                        maxId = n.notificationId();
                    }
                }
            }

            if (!newNotifs.isEmpty()) {
                lastSeenNotificationId = maxId;
                log.info("Notification polling: {} new notification(s) detected for [{}] (highestId: {})",
                    newNotifs.size(), activeUser.email(), maxId);

                // Dispatch chronologically (oldest of the new batch first)
                Collections.reverse(newNotifs);
                for (Notification n : newNotifs) {
                    log.info("Dispatching desktop & in-app alert for notification #{}", n.notificationId());
                    DesktopNotifier.showNotification("StaySync Alert", n.message());
                    showToastNotification("New Notification", n.message());
                }
                refreshViewsForRole(activeUser);
            }
        }, SwingUtilities::invokeLater);
    }

    private static void updateNotificationBadge(int unreadCount) {
        if (notificationBadgeLabel != null) {
            if (unreadCount > 0) {
                notificationBadgeLabel.setText(unreadCount > 9 ? " 9+ " : " " + unreadCount + " ");
                notificationBadgeLabel.setForeground(Color.WHITE);
                notificationBadgeLabel.setBackground(new Color(0xDC, 0x26, 0x26));
                notificationBadgeLabel.setOpaque(true);
                notificationBadgeLabel.setVisible(true);
            } else {
                notificationBadgeLabel.setVisible(false);
            }
            if (bellPanel != null) {
                bellPanel.revalidate();
                bellPanel.repaint();
            }
        }
    }

    private static void showToastNotification(String title, String message) {
        if (mainFrame == null || !mainFrame.isShowing()) return;

        SwingUtilities.invokeLater(() -> {
            if (activeToastWindow != null) {
                activeToastWindow.dispose();
                activeToastWindow = null;
            }

            JWindow toast = new JWindow(mainFrame);
            activeToastWindow = toast;

            JPanel content = new JPanel(new BorderLayout(Tokens.space(2), 0));
            content.setBackground(Tokens.surface());
            content.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Tokens.forestGreen(), 2, true),
                    BorderFactory.createEmptyBorder(Tokens.space(3), Tokens.space(4), Tokens.space(3), Tokens.space(4))
            ));

            JLabel icon = new JLabel(Icons.get("bell", 18, Tokens.forestGreen()));

            JPanel textPanel = new JPanel();
            textPanel.setOpaque(false);
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

            // High-contrast title: bright emerald (#34D399)
            JLabel titleLbl = new JLabel(title);
            titleLbl.setFont(Fonts.smMedium());
            titleLbl.setForeground(new Color(0x34, 0xD3, 0x99));

            // High-contrast body: crisp white (#F9FAFB) with multi-line HTML wrapping
            String safeMsg = message != null
                ? message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                : "";
            JLabel msgLbl = new JLabel("<html><body style='width: 250px; color: #F9FAFB; font-size: 11px; line-height: 1.35;'>" + safeMsg + "</body></html>");
            msgLbl.setFont(Fonts.xs());
            msgLbl.setForeground(new Color(0xF9, 0xFA, 0xFB));

            textPanel.add(titleLbl);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(msgLbl);

            // High-contrast close button: light gray (#E5E7EB) with hover transition to pure white (#FFFFFF)
            JButton closeBtn = new JButton("×");
            closeBtn.setFont(Fonts.monoBold(14));
            Color closeDefaultColor = new Color(0xE5, 0xE7, 0xEB);
            Color closeHoverColor = new Color(0xFF, 0xFF, 0xFF);
            closeBtn.setForeground(closeDefaultColor);
            closeBtn.setContentAreaFilled(false);
            closeBtn.setBorderPainted(false);
            closeBtn.setFocusPainted(false);
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    closeBtn.setForeground(closeHoverColor);
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    closeBtn.setForeground(closeDefaultColor);
                }
            });
            closeBtn.addActionListener(e -> {
                toast.dispose();
                if (activeToastWindow == toast) activeToastWindow = null;
            });

            content.add(icon, BorderLayout.WEST);
            content.add(textPanel, BorderLayout.CENTER);
            content.add(closeBtn, BorderLayout.EAST);

            toast.setContentPane(content);
            toast.pack();

            try {
                Point frameLoc = mainFrame.getLocationOnScreen();
                int x = frameLoc.x + mainFrame.getWidth() - toast.getWidth() - UIScale.scale(24);
                int y = frameLoc.y + mainFrame.getHeight() - toast.getHeight() - UIScale.scale(42);
                toast.setLocation(x, y);
                toast.setVisible(true);

                javax.swing.Timer dismissTimer = new javax.swing.Timer(6000, e -> {
                    toast.dispose();
                    if (activeToastWindow == toast) activeToastWindow = null;
                });
                dismissTimer.setRepeats(false);
                dismissTimer.start();
            } catch (Exception ex) {
                log.warn("Could not position notification toast: {}", ex.getMessage());
            }
        });
    }

    private static void showNotificationDropdown(Component anchor) {
        if (activeUser == null) return;

        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(Tokens.border(), 1, true));
        popup.setBackground(Tokens.surface());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Tokens.bgSubtle());
        header.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(12), UIScale.scale(8), UIScale.scale(12)));

        int unread = 0;
        try {
            unread = notificationService.getUnreadCount();
        } catch (Exception ignored) {}

        JLabel title = new JLabel("Notifications (" + unread + " unread)");
        title.setFont(Fonts.smMedium());
        title.setForeground(Tokens.text());

        JButton markAllBtn = new JButton("Mark all read");
        markAllBtn.setFont(Fonts.xs());
        markAllBtn.setForeground(Tokens.forestGreen());
        markAllBtn.setContentAreaFilled(false);
        markAllBtn.setBorderPainted(false);
        markAllBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        markAllBtn.addActionListener(ev -> {
            try {
                notificationService.markAllAsRead();
                updateNotificationBadge(0);
                popup.setVisible(false);
            } catch (Exception ex) {
                log.warn("Failed to mark all as read: {}", ex.getMessage());
            }
        });

        header.add(title, BorderLayout.WEST);
        if (unread > 0) {
            header.add(markAllBtn, BorderLayout.EAST);
        }

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(Tokens.surface());

        List<Notification> notifs;
        try {
            notifs = notificationService.getMyNotifications(12);
        } catch (Exception ex) {
            notifs = List.of();
        }

        if (notifs.isEmpty()) {
            JPanel emptyItem = new JPanel(new FlowLayout(FlowLayout.CENTER));
            emptyItem.setBackground(Tokens.surface());
            emptyItem.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(16), UIScale.scale(16), UIScale.scale(16), UIScale.scale(16)));
            JLabel emptyLbl = new JLabel("No notifications yet.");
            emptyLbl.setFont(Fonts.sm());
            emptyLbl.setForeground(Tokens.textMuted());
            emptyItem.add(emptyLbl);
            listPanel.add(emptyItem);
        } else {
            for (Notification n : notifs) {
                JPanel item = new JPanel(new BorderLayout(Tokens.space(2), 0));
                item.setBackground(n.isRead() ? Tokens.surface() : Tokens.bgSubtle());
                item.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.border()),
                        BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(12), UIScale.scale(8), UIScale.scale(12))
                ));
                item.setPreferredSize(new Dimension(UIScale.scale(340), UIScale.scale(56)));

                JLabel dot = new JLabel(n.isRead() ? "○" : "●");
                dot.setFont(Fonts.xs());
                dot.setForeground(n.isRead() ? Tokens.textFaint() : Tokens.forestGreen());

                JPanel textCol = new JPanel();
                textCol.setOpaque(false);
                textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

                JLabel msgLbl = new JLabel("<html><body style='width: 270px; font-size: 11px;'>" + n.message() + "</body></html>");
                msgLbl.setFont(n.isRead() ? Fonts.xs() : Fonts.sm());
                msgLbl.setForeground(Tokens.text());

                textCol.add(msgLbl);

                item.add(dot, BorderLayout.WEST);
                item.add(textCol, BorderLayout.CENTER);

                if (!n.isRead()) {
                    item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    item.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent me) {
                            try {
                                notificationService.markAsRead(n.notificationId());
                                dot.setText("○");
                                dot.setForeground(Tokens.textFaint());
                                item.setBackground(Tokens.surface());
                                int newUnread = Math.max(0, notificationService.getUnreadCount());
                                updateNotificationBadge(newUnread);
                            } catch (Exception ex) {
                                log.warn("Failed to mark notification read: {}", ex.getMessage());
                            }
                        }
                    });
                }
                listPanel.add(item);
            }
        }

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        int popupHeight = Math.min(UIScale.scale(340), UIScale.scale(50 + Math.max(1, notifs.size()) * 56));
        scrollPane.setPreferredSize(new Dimension(UIScale.scale(360), popupHeight));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel container = new JPanel(new BorderLayout());
        container.add(header, BorderLayout.NORTH);
        container.add(scrollPane, BorderLayout.CENTER);

        popup.add(container);
        popup.show(anchor, 0, anchor.getHeight() + UIScale.scale(4));
    }

    // ==========================================================================
    // FILTER DIALOG (CATEGORY, PRIORITY, STATUS)
    // ==========================================================================

    private static void showFilterDialog() {
        if (activeUser == null) return;

        JDialog dialog = new JDialog(mainFrame, "Filter Complaints", true);
        dialog.getContentPane().setBackground(Tokens.bg());
        dialog.setSize(UIScale.scale(420), UIScale.scale(380));
        dialog.setLocationRelativeTo(mainFrame);

        JPanel content = new JPanel();
        content.setBackground(Tokens.bg());
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(4), Tokens.space(4)));

        Card card = new Card(new BorderLayout(), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.border(), 1, true),
                BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(4), Tokens.space(4))
        ));

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel("Filter Complaints Queue");
        titleLbl.setFont(Fonts.h3());
        titleLbl.setForeground(Tokens.text());
        form.add(titleLbl);
        form.add(Box.createVerticalStrut(Tokens.space(3)));

        // 1. Category Filter
        JLabel catLbl = new JLabel("CATEGORY");
        catLbl.setFont(Fonts.xs());
        catLbl.setForeground(Tokens.textMuted());
        form.add(catLbl);
        form.add(Box.createVerticalStrut(2));

        JComboBox<CategoryOption> catCombo = new JComboBox<>();
        catCombo.setFont(Fonts.sm());
        catCombo.addItem(new CategoryOption(null, "All Categories"));
        try {
            boolean canConfidential = activeUser.canViewConfidential();
            for (Category c : categoryDao.findActive()) {
                if (c.isConfidential() && !canConfidential && !activeUser.isStudent()) {
                    continue;
                }
                CategoryOption opt = new CategoryOption(c.categoryId(), (c.isConfidential() ? "🔒 " : "") + c.categoryName());
                catCombo.addItem(opt);
                if (activeFilterCategoryId != null && activeFilterCategoryId.equals(c.categoryId())) {
                    catCombo.setSelectedItem(opt);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to load categories for filter: {}", ex.getMessage());
        }
        form.add(catCombo);
        form.add(Box.createVerticalStrut(Tokens.space(3)));

        // 2. Priority Filter
        JLabel prioLbl = new JLabel("PRIORITY");
        prioLbl.setFont(Fonts.xs());
        prioLbl.setForeground(Tokens.textMuted());
        form.add(prioLbl);
        form.add(Box.createVerticalStrut(2));

        JComboBox<String> prioCombo = new JComboBox<>(new String[]{"All Priorities", "High", "Medium", "Low"});
        prioCombo.setFont(Fonts.sm());
        if (activeFilterPriority != null) {
            prioCombo.setSelectedItem(activeFilterPriority.dbValue());
        }
        form.add(prioCombo);
        form.add(Box.createVerticalStrut(Tokens.space(3)));

        // 3. Status Filter
        JLabel statusLbl = new JLabel("STATUS");
        statusLbl.setFont(Fonts.xs());
        statusLbl.setForeground(Tokens.textMuted());
        form.add(statusLbl);
        form.add(Box.createVerticalStrut(2));

        JComboBox<String> statusCombo = new JComboBox<>(new String[]{"All Statuses", "Pending", "In Progress", "Resolved"});
        statusCombo.setFont(Fonts.sm());
        if (activeFilterStatus != null) {
            statusCombo.setSelectedItem(activeFilterStatus.dbValue());
        }
        form.add(statusCombo);
        form.add(Box.createVerticalStrut(Tokens.space(4)));

        // Action Buttons Row
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.space(2), 0));
        btnRow.setOpaque(false);

        AppButton clearBtn = new AppButton("Clear Filters", AppButton.Variant.SECONDARY, AppButton.Size.SM);
        clearBtn.addActionListener(e -> {
            activeFilterCategoryId = null;
            activeFilterPriority = null;
            activeFilterStatus = null;
            if (topFilterBtn != null) {
                topFilterBtn.setBackground(null);
                topFilterBtn.setOpaque(false);
            }
            dialog.dispose();
            refreshViewsForRole(activeUser);
        });

        AppButton applyBtn = new AppButton("Apply Filters", AppButton.Variant.PRIMARY, AppButton.Size.SM);
        applyBtn.addActionListener(e -> {
            CategoryOption selCat = (CategoryOption) catCombo.getSelectedItem();
            activeFilterCategoryId = (selCat != null && selCat.id() != null) ? selCat.id() : null;

            String selPrio = (String) prioCombo.getSelectedItem();
            activeFilterPriority = (selPrio != null && !"All Priorities".equals(selPrio)) ? Priority.fromDb(selPrio) : null;

            String selStatus = (String) statusCombo.getSelectedItem();
            activeFilterStatus = (selStatus != null && !"All Statuses".equals(selStatus)) ? Status.fromDb(selStatus) : null;

            boolean hasFilter = activeFilterCategoryId != null || activeFilterPriority != null || activeFilterStatus != null;
            if (topFilterBtn != null) {
                topFilterBtn.setBackground(hasFilter ? Tokens.softSage() : null);
                topFilterBtn.setOpaque(hasFilter);
            }

            dialog.dispose();
            refreshViewsForRole(activeUser);
        });

        btnRow.add(clearBtn);
        btnRow.add(applyBtn);
        form.add(btnRow);

        card.add(form, BorderLayout.CENTER);
        content.add(card);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setVisible(true);
    }

    // ==========================================================================
    // SCREEN: AUDIT LOGS & TRANSITION HISTORY SCREEN (ADMIN)
    // ==========================================================================

    private static JScrollPane createAuditLogsScrollableScreen() {
        ScrollablePanel scrollContent = new ScrollablePanel(new BorderLayout());
        scrollContent.setBackground(Tokens.bg());
        scrollContent.setOpaque(true);

        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(4), Tokens.space(6), Tokens.space(4)));

        Card card = new Card(new BorderLayout(0, Tokens.space(3)), Card.Variant.DEFAULT, false);
        card.setBorder(BorderFactory.createEmptyBorder(Tokens.space(4), Tokens.space(5), Tokens.space(4), Tokens.space(5)));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.space(2), 0));
        leftHeader.setOpaque(false);

        JLabel title = new JLabel("●  Audit Logs & State Transition Trail");
        title.setFont(Fonts.h2());
        title.setForeground(Tokens.text());

        boolean canConfidential = activeUser != null && activeUser.canViewConfidential();
        List<HistoryEntry> logs;
        try {
            logs = historyDao.findRecentAuditLogs(canConfidential, 50);
        } catch (Exception e) {
            log.warn("Failed to load audit history: {}", e.getMessage());
            logs = List.of();
        }

        JLabel countBadge = new JLabel("Showing " + logs.size() + " audit events");
        countBadge.setFont(Fonts.xs());
        countBadge.setForeground(Tokens.textMuted());
        countBadge.setBackground(Tokens.bgSubtle());
        countBadge.setOpaque(true);
        countBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        leftHeader.add(title);
        leftHeader.add(countBadge);

        JButton refreshBtn = createIconButton("refresh-cw", 12);
        refreshBtn.setBorder(BorderFactory.createLineBorder(Tokens.border(), 1, true));
        refreshBtn.addActionListener(e -> {
            if (activeUser != null) refreshViewsForRole(activeUser);
        });

        header.add(leftHeader, BorderLayout.WEST);
        header.add(refreshBtn, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel tablePanel = new JPanel();
        tablePanel.setOpaque(false);
        tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.Y_AXIS));

        JPanel th = new JPanel(new GridBagLayout());
        th.setBackground(Tokens.bgSubtle());
        th.setOpaque(true);
        th.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.border()),
                BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(10), UIScale.scale(8), UIScale.scale(10))
        ));

        addTableColumn(th, createHeaderCell("TIMESTAMP"), 0, 0.0, UIScale.scale(145), GridBagConstraints.WEST);
        addTableColumn(th, createHeaderCell("TICKET"), 1, 0.0, UIScale.scale(85), GridBagConstraints.WEST);
        addTableColumn(th, createHeaderCell("ACTOR"), 2, 0.0, UIScale.scale(170), GridBagConstraints.WEST);
        addTableColumn(th, createHeaderCell("TRANSITION"), 3, 0.0, UIScale.scale(200), GridBagConstraints.WEST);
        addTableColumn(th, createHeaderCell("NOTE / REMARKS"), 4, 1.0, 0, GridBagConstraints.WEST);

        tablePanel.add(th);

        if (logs.isEmpty()) {
            tablePanel.add(new EmptyState("No Audit Records", "Queue Empty", "No status updates or audit history entries recorded."));
        } else {
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
                    .withZone(java.time.ZoneId.systemDefault());

            for (HistoryEntry entry : logs) {
                JPanel row = new JPanel(new GridBagLayout());
                row.setBackground(Tokens.surface());
                row.setOpaque(true);
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.border()),
                        BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(10), UIScale.scale(8), UIScale.scale(10))
                ));

                JLabel timeLbl = new JLabel(dtf.format(entry.changedAt()));
                timeLbl.setFont(Fonts.xs());
                timeLbl.setForeground(Tokens.textMuted());

                JLabel tktLbl = new JLabel("#TKT-" + entry.complaintId());
                tktLbl.setFont(Fonts.monoBold(12));
                tktLbl.setForeground(Tokens.text());

                String actorText = (entry.actorName() != null ? entry.actorName() : "User #" + entry.changedBy())
                        + (entry.actorRole() != null ? " (" + entry.actorRole().dbValue() + ")" : "");
                JLabel actorLbl = new JLabel(actorText);
                actorLbl.setFont(Fonts.sm());
                actorLbl.setForeground(Tokens.text());

                String transition = (entry.oldStatus() != null ? entry.oldStatus().dbValue() : "Submitted")
                        + "  ➔  " + (entry.newStatus() != null ? entry.newStatus().dbValue() : "-");
                JLabel transLbl = new JLabel(transition);
                transLbl.setFont(Fonts.smMedium());
                transLbl.setForeground(Tokens.forestGreen());

                String noteText = entry.changeNote() != null ? entry.changeNote() : "—";
                JLabel noteLbl = new JLabel("<html><body style='width: 320px; font-size: 11px; color: #66685F;'>" + noteText + "</body></html>");
                noteLbl.setFont(Fonts.xs());

                addTableColumn(row, timeLbl, 0, 0.0, UIScale.scale(145), GridBagConstraints.WEST);
                addTableColumn(row, tktLbl, 1, 0.0, UIScale.scale(85), GridBagConstraints.WEST);
                addTableColumn(row, actorLbl, 2, 0.0, UIScale.scale(170), GridBagConstraints.WEST);
                addTableColumn(row, transLbl, 3, 0.0, UIScale.scale(200), GridBagConstraints.WEST);
                addTableColumn(row, noteLbl, 4, 1.0, 0, GridBagConstraints.WEST);

                tablePanel.add(row);
            }
        }

        card.add(tablePanel, BorderLayout.CENTER);
        container.add(card);

        scrollContent.add(container, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(true);
        scrollPane.setBackground(Tokens.bg());
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(Tokens.bg());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        return scrollPane;
    }

    private record NotificationPollData(int unreadCount, List<Notification> recent) {}

    private record CategoryOption(Long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
