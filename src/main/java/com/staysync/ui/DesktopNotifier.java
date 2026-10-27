package com.staysync.ui;

import com.staysync.ui.theme.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages native desktop tray and toast notifications for StaySync.
 * Uses standard java.awt.SystemTray, TrayIcon.displayMessage(), and a native Windows
 * WinRT Toast fallback via background PowerShell execution to deliver real desktop
 * notifications even when the window is minimized, running in the background, or when
 * the tray icon is located inside the taskbar overflow chevron.
 */
public final class DesktopNotifier {

    private static final Logger log = LoggerFactory.getLogger(DesktopNotifier.class);

    private static volatile TrayIcon trayIcon;
    private static volatile boolean initialized = false;
    private static final Object lock = new Object();

    private DesktopNotifier() {
        // Utility class
    }

    /**
     * Initializes the system tray icon if supported by the OS environment.
     *
     * @param frame reference to the main application frame for restore-on-click
     */
    public static void init(JFrame frame) {
        if (!SystemTray.isSupported()) {
            log.info("Native SystemTray is not supported on this platform. Desktop popups disabled; falling back to in-app toasts.");
            return;
        }

        synchronized (lock) {
            if (initialized && trayIcon != null) {
                return;
            }

            try {
                SystemTray tray = SystemTray.getSystemTray();
                Image image = createTrayBadgeImage();
                trayIcon = new TrayIcon(image, "StaySync — Hostel Resolution Suite");
                trayIcon.setImageAutoSize(true);

                // Clicking the tray icon restores StaySync to foreground
                trayIcon.addActionListener(e -> {
                    SwingUtilities.invokeLater(() -> {
                        if (frame != null) {
                            if (frame.getState() == Frame.ICONIFIED) {
                                frame.setState(Frame.NORMAL);
                            }
                            frame.setVisible(true);
                            frame.toFront();
                            frame.requestFocus();
                        }
                    });
                });

                tray.add(trayIcon);
                initialized = true;
                log.info("Native Windows SystemTray notification provider initialized successfully.");
            } catch (AWTException e) {
                log.warn("Failed to attach TrayIcon to SystemTray: {}", e.getMessage());
                trayIcon = null;
            } catch (Exception e) {
                log.warn("Unexpected error initializing SystemTray: {}", e.getMessage());
                trayIcon = null;
            }
        }
    }

    /**
     * Displays a native desktop notification balloon/toast.
     * Dual-dispatches via AWT TrayIcon and native Windows WinRT Toast on Windows platforms
     * to guarantee pop-up delivery even when the tray icon resides in the taskbar overflow drawer.
     *
     * @param title   notification header
     * @param message notification body text
     */
    public static void showNotification(String title, String message) {
        log.info("DesktopNotifier: Request to show notification - title: [{}], message length: {}",
            title, message != null ? message.length() : 0);

        // 1. Dispatch via Java AWT SystemTray TrayIcon if available
        if (trayIcon != null && SystemTray.isSupported()) {
            try {
                trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
                log.debug("DesktopNotifier: TrayIcon.displayMessage dispatched successfully.");
            } catch (Exception e) {
                log.warn("DesktopNotifier: Failed to display native tray notification: {}", e.getMessage());
            }
        } else {
            log.debug("DesktopNotifier: AWT TrayIcon not active or unsupported (trayIcon={}, supported={})",
                trayIcon != null, SystemTray.isSupported());
        }

        // 2. On Windows, also dispatch via native Windows WinRT Toast (handles minimized apps and overflow icon placement)
        if (isWindows()) {
            triggerWindowsToastAsync(title, message);
        }
    }

    /**
     * Triggers a native Windows 10/11 Action Center toast notification asynchronously off the EDT
     * using the built-in Windows.UI.Notifications WinRT API via a background hidden PowerShell process.
     */
    private static void triggerWindowsToastAsync(String title, String message) {
        CompletableFuture.runAsync(() -> {
            try {
                String safeTitle = (title != null && !title.isBlank() ? title : "StaySync Alert").replace("'", "''");
                String safeMsg = (message != null ? message : "").replace("'", "''");

                String psScript = """
                    [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
                    $template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
                    $nodes = $template.GetElementsByTagName('text')
                    $nodes.Item(0).AppendChild($template.CreateTextNode('%s')) | Out-Null
                    $nodes.Item(1).AppendChild($template.CreateTextNode('%s')) | Out-Null
                    $toast = [Windows.UI.Notifications.ToastNotification]::new($template)
                    [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('StaySync').Show($toast)
                    """.formatted(safeTitle, safeMsg);

                String encoded = Base64.getEncoder().encodeToString(psScript.getBytes(StandardCharsets.UTF_16LE));
                ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-ExecutionPolicy", "Bypass",
                    "-NoProfile",
                    "-NonInteractive",
                    "-WindowStyle", "Hidden",
                    "-EncodedCommand", encoded
                );
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process process = pb.start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                }
                log.debug("DesktopNotifier: Native Windows WinRT toast dispatched (exitCode: {})",
                    finished ? process.exitValue() : -1);
            } catch (Exception e) {
                log.debug("DesktopNotifier: Windows WinRT toast execution notice: {}", e.getMessage());
            }
        });
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    /**
     * Releases the tray icon during application shutdown.
     */
    public static void dispose() {
        synchronized (lock) {
            if (trayIcon != null && SystemTray.isSupported()) {
                try {
                    SystemTray.getSystemTray().remove(trayIcon);
                } catch (Exception ignored) {}
                trayIcon = null;
                initialized = false;
                log.info("Native Windows SystemTray notification provider disposed.");
            }
        }
    }

    public static boolean isAvailable() {
        return trayIcon != null;
    }

    private static Image createTrayBadgeImage() {
        int size = 32;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Forest green rounded square badge
        g2.setColor(Tokens.forestGreen());
        g2.fillRoundRect(2, 2, size - 4, size - 4, 8, 8);

        // White StaySync sync arc symbol
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = size / 2;
        int cy = size / 2;
        g2.drawArc(cx - 7, cy - 4, 14, 14, 40, 100);
        g2.drawArc(cx - 4, cy - 1, 8, 8, 40, 100);
        g2.fillOval(cx - 1, cy + 5, 3, 3);

        g2.dispose();
        return img;
    }
}
