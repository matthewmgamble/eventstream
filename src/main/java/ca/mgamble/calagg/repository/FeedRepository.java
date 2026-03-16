package ca.mgamble.calagg.repository;

import ca.mgamble.calagg.config.DataSource;
import ca.mgamble.calagg.model.CalendarFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FeedRepository {

    private static final Logger log = LoggerFactory.getLogger(FeedRepository.class);
    private final DataSource dataSource;

    public FeedRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<CalendarFeed> findAll() {
        List<CalendarFeed> feeds = new ArrayList<>();
        String sql = "SELECT * FROM calendar_feeds ORDER BY name";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                feeds.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to fetch feeds", e);
        }
        return feeds;
    }

    public List<CalendarFeed> findEnabled() {
        List<CalendarFeed> feeds = new ArrayList<>();
        String sql = "SELECT * FROM calendar_feeds WHERE enabled = TRUE ORDER BY name";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                feeds.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to fetch enabled feeds", e);
        }
        return feeds;
    }

    public Optional<CalendarFeed> findById(long id) {
        String sql = "SELECT * FROM calendar_feeds WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch feed {}", id, e);
        }
        return Optional.empty();
    }

    public long insert(CalendarFeed feed) {
        String sql = "INSERT INTO calendar_feeds (name, url, color, auth_type, auth_value, enabled) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, feed.getName());
            ps.setString(2, feed.getUrl());
            ps.setString(3, feed.getColor() != null ? feed.getColor() : "#3B82F6");
            ps.setString(4, feed.getAuthType() != null ? feed.getAuthType() : "NONE");
            ps.setString(5, feed.getAuthValue());
            ps.setBoolean(6, feed.isEnabled());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to insert feed", e);
        }
        return -1;
    }

    public void updateSyncStatus(long feedId, String status, String error) {
        String sql = "UPDATE calendar_feeds SET last_synced = NOW(), last_status = ?, last_error = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, error);
            ps.setLong(3, feedId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update sync status for feed {}", feedId, e);
        }
    }

    public void toggleEnabled(long feedId) {
        String sql = "UPDATE calendar_feeds SET enabled = NOT enabled WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, feedId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to toggle feed {}", feedId, e);
        }
    }

    public void delete(long feedId) {
        String sql = "DELETE FROM calendar_feeds WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, feedId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete feed {}", feedId, e);
        }
    }

    private CalendarFeed mapRow(ResultSet rs) throws SQLException {
        CalendarFeed feed = new CalendarFeed();
        feed.setId(rs.getLong("id"));
        feed.setName(rs.getString("name"));
        feed.setUrl(rs.getString("url"));
        feed.setColor(rs.getString("color"));
        feed.setAuthType(rs.getString("auth_type"));
        feed.setAuthValue(rs.getString("auth_value"));
        feed.setEnabled(rs.getBoolean("enabled"));
        Timestamp lastSynced = rs.getTimestamp("last_synced");
        if (lastSynced != null) feed.setLastSynced(lastSynced.toLocalDateTime());
        feed.setLastStatus(rs.getString("last_status"));
        feed.setLastError(rs.getString("last_error"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) feed.setCreatedAt(createdAt.toLocalDateTime());
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) feed.setUpdatedAt(updatedAt.toLocalDateTime());
        return feed;
    }
}
