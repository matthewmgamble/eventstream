package ca.mgamble.calagg.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DataSource {

    private static final Logger log = LoggerFactory.getLogger(DataSource.class);
    private final HikariDataSource hikariDataSource;

    public DataSource(AppConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getDbUrl());
        hikariConfig.setUsername(config.getDbUser());
        hikariConfig.setPassword(config.getDbPassword());
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);

        this.hikariDataSource = new HikariDataSource(hikariConfig);

        runMigrations();
    }

    private void runMigrations() {
        log.info("Running Flyway migrations...");
        Flyway flyway = Flyway.configure()
                .dataSource(hikariDataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        log.info("Flyway migrations complete.");
    }

    public Connection getConnection() throws SQLException {
        return hikariDataSource.getConnection();
    }

    public void close() {
        hikariDataSource.close();
    }
}
