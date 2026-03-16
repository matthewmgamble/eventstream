package ca.mgamble.calagg.config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AppConfig {

    private final Properties properties;

    public AppConfig() {
        this.properties = new Properties();
        // Load from classpath first
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
        // Override with external file if present (same directory as jar, or explicit path)
        Path externalConfig = Path.of("application.properties");
        if (System.getProperty("config.file") != null) {
            externalConfig = Path.of(System.getProperty("config.file"));
        }
        if (Files.exists(externalConfig)) {
            try (InputStream is = Files.newInputStream(externalConfig)) {
                properties.load(is);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load external config: " + externalConfig, e);
            }
        }
        // Environment variables override file properties
        overrideFromEnv("DB_URL", "db.url");
        overrideFromEnv("DB_USER", "db.user");
        overrideFromEnv("DB_PASSWORD", "db.password");
        overrideFromEnv("SERVER_PORT", "server.port");
        overrideFromEnv("SYNC_INTERVAL_MINUTES", "sync.interval.minutes");
        overrideFromEnv("SYNC_EXPANSION_MONTHS", "sync.expansion.months");
        overrideFromEnv("PRUNE_RETENTION_DAYS", "prune.retention.days");
        overrideFromEnv("DISPLAY_TIMEZONE", "display.timezone");
    }

    private void overrideFromEnv(String envKey, String propKey) {
        String value = System.getenv(envKey);
        if (value != null && !value.isBlank()) {
            properties.setProperty(propKey, value);
        }
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) return defaultValue;
        return Integer.parseInt(value.trim());
    }

    public String getDbUrl() { return get("db.url"); }
    public String getDbUser() { return get("db.user"); }
    public String getDbPassword() { return get("db.password"); }
    public int getServerPort() { return getInt("server.port", 8080); }
    public int getSyncIntervalMinutes() { return getInt("sync.interval.minutes", 60); }
    public int getSyncExpansionMonths() { return getInt("sync.expansion.months", 6); }
    public int getPruneRetentionDays() { return getInt("prune.retention.days", 90); }
    public String getDisplayTimezone() { return get("display.timezone", java.time.ZoneId.systemDefault().getId()); }
}
