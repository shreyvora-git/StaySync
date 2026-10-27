package com.staysync.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Application configuration loader for StaySync.
 * Reads external configuration from config/app.properties.
 * Never logs credentials or hashes.
 */
public final class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final String DEFAULT_CONFIG_PATH = "config/app.properties";

    private static volatile AppConfig instance;

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int dbPoolSize;
    private final Path uploadsDir;
    private final String filesKey;
    private final int loginMaxAttempts;
    private final int loginWindowMinutes;
    private final int sessionIdleMinutes;
    private final boolean configLoaded;

    private AppConfig(Properties props, boolean loaded) {
        this.configLoaded = loaded;
        this.dbUrl = props.getProperty("db.url", "jdbc:mysql://localhost:3306/staysync_db?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        this.dbUser = props.getProperty("db.user", "staysync_app");
        this.dbPassword = props.getProperty("db.password", "");
        this.dbPoolSize = parseInt(props.getProperty("db.pool.size"), 5);
        this.uploadsDir = Paths.get(props.getProperty("uploads.dir", "./uploads"));
        this.filesKey = props.getProperty("files.key", "");
        this.loginMaxAttempts = parseInt(props.getProperty("login.maxAttempts"), 5);
        this.loginWindowMinutes = parseInt(props.getProperty("login.windowMinutes"), 15);
        this.sessionIdleMinutes = parseInt(props.getProperty("session.idleMinutes"), 30);
    }

    public static synchronized AppConfig load() {
        if (instance == null) {
            instance = loadFromPath(DEFAULT_CONFIG_PATH);
        }
        return instance;
    }

    public static synchronized AppConfig loadFromPath(String pathStr) {
        Properties props = new Properties();
        Path path = Paths.get(pathStr);
        boolean loaded = false;

        if (Files.exists(path)) {
            try (InputStream in = new FileInputStream(path.toFile())) {
                props.load(in);
                loaded = true;
                log.info("Loaded configuration from {}", path.toAbsolutePath());
            } catch (IOException e) {
                log.error("Failed to read configuration file: {}", path.toAbsolutePath(), e);
            }
        } else {
            log.warn("Configuration file not found at: {}. Please copy config/app.properties.example to config/app.properties.", path.toAbsolutePath());
        }

        instance = new AppConfig(props, loaded);
        return instance;
    }

    private static int parseInt(String val, int defaultVal) {
        if (val == null || val.isBlank()) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public int getDbPoolSize() {
        return dbPoolSize;
    }

    public Path getUploadsDir() {
        return uploadsDir;
    }

    public String getFilesKey() {
        return filesKey;
    }

    public int getLoginMaxAttempts() {
        return loginMaxAttempts;
    }

    public int getLoginWindowMinutes() {
        return loginWindowMinutes;
    }

    public int getSessionIdleMinutes() {
        return sessionIdleMinutes;
    }

    public boolean isConfigLoaded() {
        return configLoaded;
    }
}
