package ca.mgamble.calagg.repository;

import ca.mgamble.calagg.config.DataSource;
import ca.mgamble.calagg.model.CalendarEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class EventRepository {

    private static final Logger log = LoggerFactory.getLogger(EventRepository.class);
    private final DataSource dataSource;

    public EventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsertBatch(List<CalendarEvent> events) {
        if (events.isEmpty()) return;

        String sql = """
            INSERT INTO calendar_events (feed_id, uid, recurrence_id, summary, description, location, dtstart, dtend, all_day, status, organizer, raw_rrule)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                summary = VALUES(summary),
                description = VALUES(description),
                location = VALUES(location),
                dtstart = VALUES(dtstart),
                dtend = VALUES(dtend),
                all_day = VALUES(all_day),
                status = VALUES(status),
                organizer = VALUES(organizer),
                raw_rrule = VALUES(raw_rrule)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (CalendarEvent event : events) {
                ps.setLong(1, event.getFeedId());
                ps.setString(2, event.getUid());
                ps.setString(3, event.getRecurrenceId());
                ps.setString(4, event.getSummary());
                ps.setString(5, event.getDescription());
                ps.setString(6, event.getLocation());
                ps.setTimestamp(7, Timestamp.valueOf(event.getDtstart()));
                ps.setTimestamp(8, Timestamp.valueOf(event.getDtend()));
                ps.setBoolean(9, event.isAllDay());
                ps.setString(10, event.getStatus());
                ps.setString(11, event.getOrganizer());
                ps.setString(12, event.getRawRrule());
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            conn.commit();
            log.info("Upserted {} events ({} batch results)", events.size(), results.length);
        } catch (SQLException e) {
            log.error("Failed to upsert events batch: {} (SQLState: {}, ErrorCode: {})",
                    e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
        }
    }

    public void deleteStaleEvents(long feedId, Set<String> currentKeys) {
        // currentKeys are "uid|recurrence_id" strings
        // Delete events for this feed that are NOT in the current set
        List<CalendarEvent> existing = findByFeedId(feedId);
        List<Long> toDelete = existing.stream()
                .filter(e -> {
                    String key = e.getUid() + "|" + (e.getRecurrenceId() != null ? e.getRecurrenceId() : "");
                    return !currentKeys.contains(key);
                })
                .map(CalendarEvent::getId)
                .collect(Collectors.toList());

        if (toDelete.isEmpty()) return;

        String placeholders = toDelete.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "DELETE FROM calendar_events WHERE id IN (" + placeholders + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < toDelete.size(); i++) {
                ps.setLong(i + 1, toDelete.get(i));
            }
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                log.info("Deleted {} stale events for feed {}", deleted, feedId);
            }
        } catch (SQLException e) {
            log.error("Failed to delete stale events for feed {}", feedId, e);
        }
    }

    public List<CalendarEvent> findByFeedId(long feedId) {
        List<CalendarEvent> events = new ArrayList<>();
        String sql = "SELECT e.*, f.name AS feed_name, f.color AS feed_color FROM calendar_events e JOIN calendar_feeds f ON e.feed_id = f.id WHERE e.feed_id = ? ORDER BY e.dtstart";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, feedId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch events for feed {}", feedId, e);
        }
        return events;
    }

    public List<CalendarEvent> findByDateRange(LocalDateTime from, LocalDateTime to, List<Long> feedIds, String query, Boolean allDay, boolean includeHidden, int limit, int offset) {
        List<CalendarEvent> events = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT e.*, f.name AS feed_name, f.color AS feed_color FROM calendar_events e JOIN calendar_feeds f ON e.feed_id = f.id WHERE e.dtstart >= ? AND e.dtstart < ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(from));
        params.add(Timestamp.valueOf(to));

        if (!includeHidden) {
            sql.append(" AND e.hidden = FALSE");
        }

        if (feedIds != null && !feedIds.isEmpty()) {
            String placeholders = feedIds.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" AND e.feed_id IN (").append(placeholders).append(")");
            params.addAll(feedIds);
        }

        if (query != null && !query.isBlank()) {
            sql.append(" AND (e.summary LIKE ? OR e.description LIKE ? OR e.location LIKE ?)");
            String q = "%" + query + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }

        if (allDay != null) {
            sql.append(" AND e.all_day = ?");
            params.add(allDay);
        }

        sql.append(" ORDER BY e.dtstart LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Timestamp ts) {
                    ps.setTimestamp(i + 1, ts);
                } else if (param instanceof Long l) {
                    ps.setLong(i + 1, l);
                } else if (param instanceof Boolean b) {
                    ps.setBoolean(i + 1, b);
                } else if (param instanceof Integer n) {
                    ps.setInt(i + 1, n);
                } else {
                    ps.setString(i + 1, param.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query events", e);
        }
        return events;
    }

    public int countByDateRange(LocalDateTime from, LocalDateTime to, List<Long> feedIds, String query, Boolean allDay) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM calendar_events e WHERE e.dtstart >= ? AND e.dtstart < ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(from));
        params.add(Timestamp.valueOf(to));

        if (feedIds != null && !feedIds.isEmpty()) {
            String placeholders = feedIds.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" AND e.feed_id IN (").append(placeholders).append(")");
            params.addAll(feedIds);
        }

        if (query != null && !query.isBlank()) {
            sql.append(" AND (e.summary LIKE ? OR e.description LIKE ? OR e.location LIKE ?)");
            String q = "%" + query + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }

        if (allDay != null) {
            sql.append(" AND e.all_day = ?");
            params.add(allDay);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Timestamp ts) {
                    ps.setTimestamp(i + 1, ts);
                } else if (param instanceof Long l) {
                    ps.setLong(i + 1, l);
                } else if (param instanceof Boolean b) {
                    ps.setBoolean(i + 1, b);
                } else {
                    ps.setString(i + 1, param.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to count events", e);
        }
        return 0;
    }

    public Optional<CalendarEvent> findById(long id) {
        String sql = "SELECT e.*, f.name AS feed_name, f.color AS feed_color FROM calendar_events e JOIN calendar_feeds f ON e.feed_id = f.id WHERE e.id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch event {}", id, e);
        }
        return Optional.empty();
    }

    public void toggleHidden(long eventId) {
        String sql = "UPDATE calendar_events SET hidden = NOT hidden WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to toggle hidden for event {}", eventId, e);
        }
    }

    public void updateNotes(long eventId, String notes) {
        String sql = "UPDATE calendar_events SET notes = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, notes != null && notes.isBlank() ? null : notes);
            ps.setLong(2, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update notes for event {}", eventId, e);
        }
    }

    public int deleteOlderThan(LocalDateTime cutoff) {
        String sql = "DELETE FROM calendar_events WHERE dtend < ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(cutoff));
            return ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to prune old events", e);
            return 0;
        }
    }

    private CalendarEvent mapRow(ResultSet rs) throws SQLException {
        CalendarEvent event = new CalendarEvent();
        event.setId(rs.getLong("id"));
        event.setFeedId(rs.getLong("feed_id"));
        event.setUid(rs.getString("uid"));
        event.setRecurrenceId(rs.getString("recurrence_id"));
        event.setSummary(rs.getString("summary"));
        event.setDescription(rs.getString("description"));
        event.setLocation(rs.getString("location"));
        event.setDtstart(rs.getTimestamp("dtstart").toLocalDateTime());
        event.setDtend(rs.getTimestamp("dtend").toLocalDateTime());
        event.setAllDay(rs.getBoolean("all_day"));
        event.setStatus(rs.getString("status"));
        event.setOrganizer(rs.getString("organizer"));
        event.setRawRrule(rs.getString("raw_rrule"));
        event.setHidden(rs.getBoolean("hidden"));
        event.setNotes(rs.getString("notes"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) event.setCreatedAt(createdAt.toLocalDateTime());
        // Denormalized fields from join
        try {
            event.setFeedName(rs.getString("feed_name"));
            event.setFeedColor(rs.getString("feed_color"));
        } catch (SQLException ignored) {
            // Not all queries join the feeds table
        }
        return event;
    }
}
