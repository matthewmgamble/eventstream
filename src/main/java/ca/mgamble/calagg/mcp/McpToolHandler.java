package ca.mgamble.calagg.mcp;

import ca.mgamble.calagg.config.AppConfig;
import ca.mgamble.calagg.model.CalendarEvent;
import ca.mgamble.calagg.model.CalendarFeed;
import ca.mgamble.calagg.model.FreeTimeResponse;
import ca.mgamble.calagg.config.OfficeConfig;
import ca.mgamble.calagg.repository.EventRepository;
import ca.mgamble.calagg.repository.FeedRepository;
import ca.mgamble.calagg.repository.OfficeLocationRepository;
import ca.mgamble.calagg.service.FreeTimeService;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class McpToolHandler {

    private final EventRepository eventRepository;
    private final FeedRepository feedRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final FreeTimeService freeTimeService;
    private final AppConfig config;

    public McpToolHandler(EventRepository eventRepository, FeedRepository feedRepository,
                          OfficeLocationRepository officeLocationRepository,
                          FreeTimeService freeTimeService, AppConfig config) {
        this.eventRepository = eventRepository;
        this.feedRepository = feedRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.freeTimeService = freeTimeService;
        this.config = config;
    }

    public String handleToolCall(String toolName, Map<String, Object> arguments) {
        return switch (toolName) {
            case "get_events" -> handleGetEvents(arguments);
            case "get_today" -> handleGetToday();
            case "get_upcoming" -> handleGetUpcoming(arguments);
            case "find_free_time" -> handleFindFreeTime(arguments);
            case "list_calendars" -> handleListCalendars();
            case "get_office_location" -> handleGetOfficeLocation(arguments);
            case "set_office_location" -> handleSetOfficeLocation(arguments);
            default -> "Unknown tool: " + toolName;
        };
    }

    private String handleGetEvents(Map<String, Object> args) {
        LocalDate fromDate = parseDate(args.get("from"), LocalDate.now());
        LocalDate toDate = parseDate(args.get("to"), fromDate);
        String feedName = args.get("feed_name") != null ? args.get("feed_name").toString() : null;
        String query = args.get("q") != null ? args.get("q").toString() : null;

        List<Long> feedIds = null;
        if (feedName != null && !feedName.isBlank()) {
            feedIds = feedRepository.findAll().stream()
                    .filter(f -> f.getName().toLowerCase().contains(feedName.toLowerCase()))
                    .map(CalendarFeed::getId)
                    .toList();
            if (feedIds.isEmpty()) {
                return "No calendar found matching '" + feedName + "'. Use list_calendars to see available calendars.";
            }
        }

        ZoneId displayZone = ZoneId.of(config.getDisplayTimezone());
        LocalDateTime utcFrom = fromDate.atStartOfDay().atZone(displayZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime utcTo = toDate.plusDays(1).atStartOfDay().atZone(displayZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        List<CalendarEvent> events = eventRepository.findByDateRange(utcFrom, utcTo, feedIds, query, null, false, 500, 0);
        return formatEvents(events, displayZone);
    }

    private String handleGetToday() {
        ZoneId displayZone = ZoneId.of(config.getDisplayTimezone());
        LocalDate today = LocalDate.now(displayZone);
        LocalDateTime utcFrom = today.atStartOfDay().atZone(displayZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime utcTo = today.plusDays(1).atStartOfDay().atZone(displayZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        List<CalendarEvent> events = eventRepository.findByDateRange(utcFrom, utcTo, null, null, null, false, 500, 0);
        return formatEvents(events, displayZone);
    }

    private String handleGetUpcoming(Map<String, Object> args) {
        int days = 7;
        if (args.get("days") != null) {
            days = ((Number) args.get("days")).intValue();
        }

        ZoneId displayZone = ZoneId.of(config.getDisplayTimezone());
        LocalDate today = LocalDate.now(displayZone);
        LocalDateTime utcFrom = today.atStartOfDay().atZone(displayZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime utcTo = today.plusDays(days).atStartOfDay().atZone(displayZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        List<CalendarEvent> events = eventRepository.findByDateRange(utcFrom, utcTo, null, null, null, false, 500, 0);
        return formatEvents(events, displayZone);
    }

    private String handleFindFreeTime(Map<String, Object> args) {
        LocalDate date = parseDate(args.get("date"), LocalDate.now());
        int duration = 60;
        if (args.get("duration_minutes") != null) {
            duration = ((Number) args.get("duration_minutes")).intValue();
        }
        LocalTime dayStart = parseTime(args.get("day_start"), LocalTime.of(8, 0));
        LocalTime dayEnd = parseTime(args.get("day_end"), LocalTime.of(17, 0));

        FreeTimeResponse response = freeTimeService.findFreeSlots(date, duration, dayStart, dayEnd);
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("h:mm a");

        StringBuilder sb = new StringBuilder();
        String dayName = date.getDayOfWeek().toString().charAt(0)
                + date.getDayOfWeek().toString().substring(1).toLowerCase();
        sb.append("Free time on ").append(dayName).append(", ").append(date)
                .append(" (minimum ").append(duration).append(" minutes):\n\n");

        if (response.freeSlots().isEmpty()) {
            sb.append("No free slots available.\n");
        } else {
            for (var slot : response.freeSlots()) {
                sb.append(slot.start().format(tf)).append(" - ").append(slot.end().format(tf))
                        .append(" (").append(slot.durationMinutes()).append(" min)\n");
            }
        }

        int hours = response.totalFreeMinutes() / 60;
        int mins = response.totalFreeMinutes() % 60;
        sb.append("\nTotal free time: ").append(hours).append(" hours ").append(mins).append(" minutes\n");

        if (!response.allDayEvents().isEmpty()) {
            sb.append("\nAll-day events: ").append(String.join(", ", response.allDayEvents())).append("\n");
        }

        return sb.toString();
    }

    private String handleListCalendars() {
        List<CalendarFeed> feeds = feedRepository.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("Calendar feeds (").append(feeds.size()).append("):\n\n");
        for (CalendarFeed feed : feeds) {
            sb.append("- ").append(feed.getName());
            sb.append(" [").append(feed.isEnabled() ? "enabled" : "disabled").append("]");
            sb.append(" — Status: ").append(feed.getLastStatus());
            if (feed.getLastSynced() != null) {
                sb.append(", last synced: ").append(feed.getLastSynced());
            }
            if (feed.getLastError() != null) {
                sb.append(" (error: ").append(feed.getLastError()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String handleGetOfficeLocation(Map<String, Object> args) {
        LocalDate date = parseDate(args.get("date"), LocalDate.now());
        int days = 1;
        if (args.get("days") != null) {
            days = ((Number) args.get("days")).intValue();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Office location");
        if (days > 1) sb.append("s");
        sb.append(":\n\n");

        for (int i = 0; i < days; i++) {
            LocalDate d = date.plusDays(i);
            String location = officeLocationRepository.getLocationForDate(d);
            boolean isOverride = officeLocationRepository.findByDate(d).isPresent();
            String dayName = d.getDayOfWeek().toString().charAt(0)
                    + d.getDayOfWeek().toString().substring(1).toLowerCase();
            sb.append(dayName).append(", ").append(d).append(": ").append(location);
            if (isOverride) sb.append(" (override)");
            else sb.append(" (default)");
            sb.append("\n");
        }

        OfficeConfig oc = officeLocationRepository.getOfficeConfig();
        sb.append("\nLocations: ").append(String.join(", ", oc.getLocations()));
        sb.append(". Default: ").append(oc.getDefaultLocation()).append("\n");
        return sb.toString();
    }

    private String handleSetOfficeLocation(Map<String, Object> args) {
        LocalDate date = parseDate(args.get("date"), LocalDate.now());
        String location = args.get("location") != null ? args.get("location").toString() : null;

        if (location == null || location.isBlank()) {
            return "Error: location is required. Valid values: AJ, Oakville, Downtown, Home";
        }

        officeLocationRepository.setLocation(date, location);
        String actual = officeLocationRepository.getLocationForDate(date);

        String dayName = date.getDayOfWeek().toString().charAt(0)
                + date.getDayOfWeek().toString().substring(1).toLowerCase();
        return "Office location for " + dayName + ", " + date + " set to: " + actual + "\n";
    }

    private String formatEvents(List<CalendarEvent> events, ZoneId displayZone) {
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
                String dayName = day.getDayOfWeek().toString().charAt(0)
                        + day.getDayOfWeek().toString().substring(1).toLowerCase();
                String monthName = day.getMonth().toString().charAt(0)
                        + day.getMonth().toString().substring(1).toLowerCase();
                sb.append("Events for ").append(dayName).append(", ")
                        .append(monthName).append(" ").append(day.getDayOfMonth())
                        .append(", ").append(day.getYear()).append(":\n\n");
                currentDay = day;
            }

            if (event.isAllDay()) {
                sb.append("All day | ").append(event.getSummary() != null ? event.getSummary() : "(no title)");
            } else {
                sb.append(start.format(timeFmt)).append(" - ").append(end.format(timeFmt))
                        .append(" | ").append(event.getSummary() != null ? event.getSummary() : "(no title)");
            }
            sb.append(" (").append(event.getFeedName() != null ? event.getFeedName() : "unknown").append(")");

            if ("TENTATIVE".equalsIgnoreCase(event.getStatus())) {
                sb.append(" [tentative]");
            }
            if (event.getLocation() != null && !event.getLocation().isBlank()) {
                sb.append("\n  Location: ").append(event.getLocation());
            }
            if (event.getNotes() != null && !event.getNotes().isBlank()) {
                sb.append("\n  Note: ").append(event.getNotes());
            }
            sb.append("\n");
        }

        long count = events.stream().filter(e -> !e.isHidden()).count();
        sb.append("\n").append(count).append(" event").append(count != 1 ? "s" : "").append(" found.\n");
        return sb.toString();
    }

    private LocalDate parseDate(Object value, LocalDate defaultValue) {
        if (value == null) return defaultValue;
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private LocalTime parseTime(Object value, LocalTime defaultValue) {
        if (value == null) return defaultValue;
        try {
            return LocalTime.parse(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
