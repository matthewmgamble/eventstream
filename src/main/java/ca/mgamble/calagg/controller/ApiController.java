package ca.mgamble.calagg.controller;

import ca.mgamble.calagg.config.AppConfig;
import ca.mgamble.calagg.model.CalendarEvent;
import ca.mgamble.calagg.model.CalendarFeed;
import ca.mgamble.calagg.model.FreeTimeResponse;
import ca.mgamble.calagg.config.OfficeConfig;
import ca.mgamble.calagg.repository.EventRepository;
import ca.mgamble.calagg.repository.FeedRepository;
import ca.mgamble.calagg.repository.OfficeLocationRepository;
import ca.mgamble.calagg.scheduler.SyncScheduler;
import ca.mgamble.calagg.service.FreeTimeService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class ApiController {

    private final FeedRepository feedRepository;
    private final EventRepository eventRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final FreeTimeService freeTimeService;
    private final SyncScheduler syncScheduler;
    private final AppConfig config;
    private final Gson gson;

    public ApiController(FeedRepository feedRepository, EventRepository eventRepository,
                         OfficeLocationRepository officeLocationRepository,
                         FreeTimeService freeTimeService, SyncScheduler syncScheduler, AppConfig config) {
        this.feedRepository = feedRepository;
        this.eventRepository = eventRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.freeTimeService = freeTimeService;
        this.syncScheduler = syncScheduler;
        this.config = config;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    public void register(Javalin app) {
        app.get("/api/v1/feeds", this::listFeeds);
        app.get("/api/v1/events", this::queryEvents);
        app.get("/api/v1/events/today", this::todayEvents);
        app.get("/api/v1/events/upcoming", this::upcomingEvents);
        app.get("/api/v1/events/summary", this::summaryEvents);
        app.get("/api/v1/events/free-time", this::freeTime);
        app.get("/api/v1/office", this::getOfficeLocation);
        app.post("/api/v1/office", this::setOfficeLocation);
        app.get("/api/v1/events/{id}", this::getEvent);
        app.post("/api/v1/events/{id}/hide", this::hideEvent);
        app.post("/api/v1/events/{id}/notes", this::updateNotes);
        app.post("/api/v1/feeds/sync", this::triggerSync);
        app.get("/api/v1/health", this::health);
    }

    private void listFeeds(Context ctx) {
        List<CalendarFeed> feeds = feedRepository.findAll();
        List<Map<String, Object>> result = feeds.stream().map(this::feedToMap).collect(Collectors.toList());
        json(ctx, Map.of("feeds", result));
    }

    private void queryEvents(Context ctx) {
        LocalDateTime from = parseDateTime(ctx.queryParam("from"), LocalDate.now().atStartOfDay());
        LocalDateTime to = parseDateTime(ctx.queryParam("to"), from.plusDays(30));
        List<Long> feedIds = parseLongList(ctx.queryParam("feed_id"));
        String query = ctx.queryParam("q");
        Boolean allDay = ctx.queryParam("all_day") != null ? Boolean.parseBoolean(ctx.queryParam("all_day")) : null;
        boolean includeHidden = "true".equals(ctx.queryParam("include_hidden"));
        int limit = parseIntParam(ctx.queryParam("limit"), 200);
        int offset = parseIntParam(ctx.queryParam("offset"), 0);

        List<CalendarEvent> events = eventRepository.findByDateRange(from, to, feedIds, query, allDay, !includeHidden, limit, offset);
        int total = eventRepository.countByDateRange(from, to, feedIds, query, allDay);

        ZoneId displayZone = ZoneId.of(config.getDisplayTimezone());
        List<Map<String, Object>> eventMaps = events.stream()
                .map(e -> eventToMap(e, displayZone))
                .collect(Collectors.toList());

        String accept = ctx.header("Accept");
        if (accept != null && accept.contains("text/plain")) {
            ctx.contentType("text/plain");
            ctx.result(eventsToText(events, displayZone));
        } else {
            json(ctx, Map.of(
                    "events", eventMaps,
                    "total", total,
                    "limit", limit,
                    "offset", offset
            ));
        }
    }

    private void todayEvents(Context ctx) {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        boolean includeHidden = "true".equals(ctx.queryParam("include_hidden"));

        List<CalendarEvent> events = eventRepository.findByDateRange(from, to, null, null, null, !includeHidden, 500, 0);
        ZoneId displayZone = ZoneId.of(config.getDisplayTimezone());

        String accept = ctx.header("Accept");
        if (accept != null && accept.contains("text/plain")) {
            ctx.contentType("text/plain");
            ctx.result(eventsToText(events, displayZone));
        } else {
            json(ctx, Map.of(
                    "events", events.stream().map(e -> eventToMap(e, displayZone)).collect(Collectors.toList()),
                    "total", events.size()
            ));
        }
    }

    private void upcomingEvents(Context ctx) {
        int days = parseIntParam(ctx.queryParam("days"), 7);
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = from.plusDays(days);
        boolean includeHidden = "true".equals(ctx.queryParam("include_hidden"));

        List<CalendarEvent> events = eventRepository.findByDateRange(from, to, null, null, null, !includeHidden, 500, 0);
        ZoneId displayZone = ZoneId.of(config.getDisplayTimezone());

        String accept = ctx.header("Accept");
        if (accept != null && accept.contains("text/plain")) {
            ctx.contentType("text/plain");
            ctx.result(eventsToText(events, displayZone));
        } else {
            json(ctx, Map.of(
                    "events", events.stream().map(e -> eventToMap(e, displayZone)).collect(Collectors.toList()),
                    "total", events.size()
            ));
        }
    }

    private void freeTime(Context ctx) {
        LocalDate date;
        try {
            date = ctx.queryParam("date") != null ? LocalDate.parse(ctx.queryParam("date")) : LocalDate.now();
        } catch (DateTimeParseException e) {
            ctx.status(400);
            json(ctx, Map.of("error", "Invalid date format. Use yyyy-MM-dd."));
            return;
        }

        int duration = parseIntParam(ctx.queryParam("duration"), 60);
        if (duration <= 0) {
            ctx.status(400);
            json(ctx, Map.of("error", "Duration must be a positive integer."));
            return;
        }

        LocalTime dayStart;
        LocalTime dayEnd;
        try {
            dayStart = ctx.queryParam("day_start") != null ? LocalTime.parse(ctx.queryParam("day_start")) : LocalTime.of(8, 0);
            dayEnd = ctx.queryParam("day_end") != null ? LocalTime.parse(ctx.queryParam("day_end")) : LocalTime.of(17, 0);
        } catch (DateTimeParseException e) {
            ctx.status(400);
            json(ctx, Map.of("error", "Invalid time format. Use HH:mm."));
            return;
        }

        if (!dayStart.isBefore(dayEnd)) {
            ctx.status(400);
            json(ctx, Map.of("error", "day_start must be before day_end."));
            return;
        }

        FreeTimeResponse response = freeTimeService.findFreeSlots(date, duration, dayStart, dayEnd);
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

        String accept = ctx.header("Accept");
        if (accept != null && accept.contains("text/plain")) {
            StringBuilder sb = new StringBuilder();
            sb.append("Free time on ").append(date.getDayOfWeek().toString().charAt(0))
                    .append(date.getDayOfWeek().toString().substring(1).toLowerCase())
                    .append(", ").append(date).append(" (minimum ").append(duration).append(" minutes):\n\n");
            if (response.freeSlots().isEmpty()) {
                sb.append("No free slots available.\n");
            } else {
                for (var slot : response.freeSlots()) {
                    sb.append(slot.start().format(tf)).append(" - ").append(slot.end().format(tf))
                            .append(" (").append(slot.durationMinutes()).append(" min)\n");
                }
            }
            sb.append("\nTotal free time: ").append(response.totalFreeMinutes() / 60).append(" hours ")
                    .append(response.totalFreeMinutes() % 60).append(" minutes\n");
            if (!response.allDayEvents().isEmpty()) {
                sb.append("\nAll-day events: ").append(String.join(", ", response.allDayEvents())).append("\n");
            }
            ctx.contentType("text/plain");
            ctx.result(sb.toString());
        } else {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("date", date.toString());
            map.put("dayStart", dayStart.format(tf));
            map.put("dayEnd", dayEnd.format(tf));
            map.put("freeSlots", response.freeSlots().stream().map(s -> Map.of(
                    "start", s.start().format(tf),
                    "end", s.end().format(tf),
                    "durationMinutes", s.durationMinutes()
            )).collect(Collectors.toList()));
            map.put("totalFreeMinutes", response.totalFreeMinutes());
            map.put("allDayEvents", response.allDayEvents());
            json(ctx, map);
        }
    }

    private void summaryEvents(Context ctx) {
        int days = parseIntParam(ctx.queryParam("days"), 7);
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = from.plusDays(days);
        boolean includeHidden = "true".equals(ctx.queryParam("include_hidden"));

        List<CalendarEvent> events = eventRepository.findByDateRange(from, to, null, null, null, !includeHidden, 500, 0);
        ZoneId displayZone = ZoneId.of(config.getDisplayTimezone());

        ctx.contentType("text/plain");
        ctx.result(eventsToText(events, displayZone));
    }

    private void getEvent(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        ZoneId displayZone = ZoneId.of(config.getDisplayTimezone());
        eventRepository.findById(id).ifPresentOrElse(
                event -> json(ctx, eventToMap(event, displayZone)),
                () -> {
                    ctx.status(404);
                    json(ctx, Map.of("error", "Event not found"));
                }
        );
    }

    private void hideEvent(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        eventRepository.toggleHidden(id);
        json(ctx, Map.of("ok", true));
    }

    private void updateNotes(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        String body = ctx.body();
        // Accept either JSON {"notes": "..."} or plain text
        String notes;
        if (body.trim().startsWith("{")) {
            @SuppressWarnings("unchecked")
            Map<String, String> parsed = gson.fromJson(body, Map.class);
            notes = parsed.get("notes");
        } else {
            notes = body;
        }
        eventRepository.updateNotes(id, notes);
        json(ctx, Map.of("ok", true));
    }

    private void triggerSync(Context ctx) {
        syncScheduler.triggerNow();
        json(ctx, Map.of("ok", true, "message", "Sync triggered"));
    }

    private void getOfficeLocation(Context ctx) {
        LocalDate date;
        try {
            date = ctx.queryParam("date") != null ? LocalDate.parse(ctx.queryParam("date")) : LocalDate.now();
        } catch (DateTimeParseException e) {
            ctx.status(400);
            json(ctx, Map.of("error", "Invalid date format. Use yyyy-MM-dd."));
            return;
        }

        // Support range query
        int days = parseIntParam(ctx.queryParam("days"), 1);
        if (days == 1) {
            String location = officeLocationRepository.getLocationForDate(date);
            boolean isOverride = officeLocationRepository.findByDate(date).isPresent();
            json(ctx, Map.of(
                    "date", date.toString(),
                    "location", location,
                    "isDefault", !isOverride,
                    "dayOfWeek", date.getDayOfWeek().toString()
            ));
        } else {
            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 0; i < days; i++) {
                LocalDate d = date.plusDays(i);
                String location = officeLocationRepository.getLocationForDate(d);
                boolean isOverride = officeLocationRepository.findByDate(d).isPresent();
                results.add(Map.of(
                        "date", d.toString(),
                        "location", location,
                        "isDefault", !isOverride,
                        "dayOfWeek", d.getDayOfWeek().toString()
                ));
            }
            json(ctx, Map.of("locations", results));
        }
    }

    @SuppressWarnings("unchecked")
    private void setOfficeLocation(Context ctx) {
        String body = ctx.body();
        Map<String, String> params;
        if (body.trim().startsWith("{")) {
            params = gson.fromJson(body, Map.class);
        } else {
            params = Map.of(
                    "date", ctx.queryParam("date") != null ? ctx.queryParam("date") : "",
                    "location", ctx.queryParam("location") != null ? ctx.queryParam("location") : ""
            );
        }

        LocalDate date;
        try {
            date = params.get("date") != null && !params.get("date").isBlank()
                    ? LocalDate.parse(params.get("date")) : LocalDate.now();
        } catch (DateTimeParseException e) {
            ctx.status(400);
            json(ctx, Map.of("error", "Invalid date format. Use yyyy-MM-dd."));
            return;
        }

        String location = params.get("location");
        if (location == null || location.isBlank()) {
            ctx.status(400);
            json(ctx, Map.of("error", "Location is required. Valid: " + String.join(", ", officeLocationRepository.getOfficeConfig().getLocations())));
            return;
        }

        officeLocationRepository.setLocation(date, location);
        json(ctx, Map.of(
                "ok", true,
                "date", date.toString(),
                "location", officeLocationRepository.getLocationForDate(date)
        ));
    }

    private void health(Context ctx) {
        List<CalendarFeed> feeds = feedRepository.findAll();
        long errorCount = feeds.stream().filter(f -> "ERROR".equals(f.getLastStatus())).count();
        String status = errorCount == 0 ? "healthy" : "degraded";
        json(ctx, Map.of(
                "status", status,
                "feeds", feeds.size(),
                "feedErrors", errorCount,
                "timezone", config.getDisplayTimezone()
        ));
    }

    // --- Helpers ---

    private void json(Context ctx, Object obj) {
        ctx.contentType("application/json");
        ctx.result(gson.toJson(obj));
    }

    private Map<String, Object> feedToMap(CalendarFeed feed) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", feed.getId());
        map.put("name", feed.getName());
        map.put("color", feed.getColor());
        map.put("enabled", feed.isEnabled());
        map.put("lastSynced", feed.getLastSynced() != null ? feed.getLastSynced().toString() : null);
        map.put("lastStatus", feed.getLastStatus());
        map.put("lastError", feed.getLastError());
        return map;
    }

    private Map<String, Object> eventToMap(CalendarEvent event, ZoneId displayZone) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.getId());
        map.put("feed", Map.of(
                "id", event.getFeedId(),
                "name", event.getFeedName() != null ? event.getFeedName() : "",
                "color", event.getFeedColor() != null ? event.getFeedColor() : "#3B82F6"
        ));
        map.put("uid", event.getUid());
        map.put("summary", event.getSummary());
        map.put("description", event.getDescription());
        map.put("location", event.getLocation());
        map.put("start", formatDt(event.getDtstart(), displayZone));
        map.put("end", formatDt(event.getDtend(), displayZone));
        map.put("allDay", event.isAllDay());
        map.put("status", event.getStatus());
        map.put("hidden", event.isHidden());
        map.put("notes", event.getNotes());
        return map;
    }

    private String formatDt(LocalDateTime utc, ZoneId displayZone) {
        if (utc == null) return null;
        return utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(displayZone)
                .toLocalDateTime()
                .toString();
    }

    private String eventsToText(List<CalendarEvent> events, ZoneId displayZone) {
        if (events.isEmpty()) return "No events found.";

        StringBuilder sb = new StringBuilder();
        LocalDate currentDay = null;
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("h:mm a");

        for (CalendarEvent event : events) {
            LocalDateTime start = event.getDtstart().atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(displayZone).toLocalDateTime();
            LocalDateTime end = event.getDtend().atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(displayZone).toLocalDateTime();
            LocalDate day = start.toLocalDate();

            if (!day.equals(currentDay)) {
                if (currentDay != null) sb.append("\n");
                sb.append("## ").append(day.getDayOfWeek().toString().charAt(0))
                        .append(day.getDayOfWeek().toString().substring(1).toLowerCase())
                        .append(", ").append(day.getMonth().toString().charAt(0))
                        .append(day.getMonth().toString().substring(1).toLowerCase())
                        .append(" ").append(day.getDayOfMonth())
                        .append(", ").append(day.getYear()).append("\n");
                currentDay = day;
            }

            sb.append("- ");
            if (event.isAllDay()) {
                sb.append("[All day] ");
            } else {
                sb.append("[").append(start.format(timeFmt))
                        .append(" - ").append(end.format(timeFmt)).append("] ");
            }
            sb.append(event.getSummary() != null ? event.getSummary() : "(no title)");

            if ("TENTATIVE".equalsIgnoreCase(event.getStatus())) {
                sb.append(" (tentative)");
            }
            if (event.getFeedName() != null) {
                sb.append(" [").append(event.getFeedName()).append("]");
            }
            if (event.getLocation() != null && !event.getLocation().isBlank()) {
                sb.append(" @ ").append(event.getLocation());
            }
            if (event.getNotes() != null && !event.getNotes().isBlank()) {
                sb.append(" -- Note: ").append(event.getNotes());
            }
            sb.append("\n");
        }

        // Conflict detection
        List<String> conflicts = detectConflicts(events, displayZone);
        if (!conflicts.isEmpty()) {
            sb.append("\n## Conflicts\n");
            for (String conflict : conflicts) {
                sb.append("- ").append(conflict).append("\n");
            }
        }

        return sb.toString();
    }

    private List<String> detectConflicts(List<CalendarEvent> events, ZoneId displayZone) {
        List<String> conflicts = new ArrayList<>();
        // Only check non-all-day, non-hidden events
        List<CalendarEvent> timed = events.stream()
                .filter(e -> !e.isAllDay() && !e.isHidden())
                .toList();

        for (int i = 0; i < timed.size(); i++) {
            for (int j = i + 1; j < timed.size(); j++) {
                CalendarEvent a = timed.get(i);
                CalendarEvent b = timed.get(j);
                // Overlap: a.start < b.end && b.start < a.end
                if (a.getDtstart().isBefore(b.getDtend()) && b.getDtstart().isBefore(a.getDtend())) {
                    DateTimeFormatter tf = DateTimeFormatter.ofPattern("h:mm a");
                    LocalDateTime aStart = a.getDtstart().atZone(ZoneOffset.UTC).withZoneSameInstant(displayZone).toLocalDateTime();
                    conflicts.add(String.format("'%s' (%s) overlaps with '%s' (%s)",
                            a.getSummary(), aStart.format(tf),
                            b.getSummary(), b.getDtstart().atZone(ZoneOffset.UTC).withZoneSameInstant(displayZone).toLocalDateTime().format(tf)));
                }
            }
        }
        return conflicts;
    }

    private LocalDateTime parseDateTime(String value, LocalDateTime defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(value).atStartOfDay();
            } catch (DateTimeParseException e2) {
                return defaultValue;
            }
        }
    }

    private List<Long> parseLongList(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    private int parseIntParam(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
