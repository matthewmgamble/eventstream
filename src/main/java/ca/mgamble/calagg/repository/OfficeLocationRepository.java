package ca.mgamble.calagg.repository;

import ca.mgamble.calagg.config.DataSource;
import ca.mgamble.calagg.config.OfficeConfig;
import ca.mgamble.calagg.model.OfficeLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OfficeLocationRepository {

    private static final Logger log = LoggerFactory.getLogger(OfficeLocationRepository.class);
    private final DataSource dataSource;
    private final OfficeConfig officeConfig;

    public OfficeLocationRepository(DataSource dataSource, OfficeConfig officeConfig) {
        this.dataSource = dataSource;
        this.officeConfig = officeConfig;
    }

    public OfficeConfig getOfficeConfig() {
        return officeConfig;
    }

    /**
     * Get the office location for a date. Returns the override if set,
     * otherwise the default based on day of week.
     */
    public String getLocationForDate(LocalDate date) {
        return findByDate(date)
                .map(OfficeLocation::getLocation)
                .orElse(officeConfig.getDefaultForDate(date));
    }

    public Optional<OfficeLocation> findByDate(LocalDate date) {
        String sql = "SELECT * FROM office_locations WHERE date = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch office location for {}", date, e);
        }
        return Optional.empty();
    }

    public void setLocation(LocalDate date, String location) {
        String normalized = officeConfig.normalize(location);

        // If it matches the default, remove the override instead
        if (normalized.equals(officeConfig.getDefaultForDate(date))) {
            delete(date);
            return;
        }

        String sql = "INSERT INTO office_locations (date, location) VALUES (?, ?) ON DUPLICATE KEY UPDATE location = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ps.setString(2, normalized);
            ps.setString(3, normalized);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to set office location for {}", date, e);
        }
    }

    public void delete(LocalDate date) {
        String sql = "DELETE FROM office_locations WHERE date = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete office location for {}", date, e);
        }
    }

    public List<OfficeLocation> findByDateRange(LocalDate from, LocalDate to) {
        List<OfficeLocation> results = new ArrayList<>();
        String sql = "SELECT * FROM office_locations WHERE date >= ? AND date <= ? ORDER BY date";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch office locations", e);
        }
        return results;
    }

    private OfficeLocation mapRow(ResultSet rs) throws SQLException {
        OfficeLocation loc = new OfficeLocation();
        loc.setId(rs.getLong("id"));
        loc.setDate(rs.getDate("date").toLocalDate());
        loc.setLocation(rs.getString("location"));
        return loc;
    }
}
