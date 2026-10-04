package ca.mgamble.calagg.mcp;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;

public class CalendarTools {

    private static final Gson gson = new Gson();

    public static List<Map<String, Object>> getToolDefinitions() {
        return List.of(
            Map.of(
                "name", "get_events",
                "description", "Get calendar events within a date range. Use this to answer questions about what's scheduled on specific days or time periods. Returns events with their time, title, location, and which calendar they belong to.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "from", Map.of("type", "string", "description", "Start date in ISO format (yyyy-MM-dd). Defaults to today."),
                        "to", Map.of("type", "string", "description", "End date (inclusive) in ISO format (yyyy-MM-dd). All events on this date are included. Defaults to same as from (i.e., a single day). For tomorrow's events, set both from and to to tomorrow's date."),
                        "feed_name", Map.of("type", "string", "description", "Optional: filter to a specific calendar by name (e.g., 'Work', 'Personal')"),
                        "q", Map.of("type", "string", "description", "Optional: text search across event titles, descriptions, and locations")
                    )
                )
            ),
            Map.of(
                "name", "get_today",
                "description", "Get all of today's calendar events across all calendars. Use this when the user asks about their day, what they have coming up today, or their current schedule.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of()
                )
            ),
            Map.of(
                "name", "get_upcoming",
                "description", "Get upcoming events for the next N days. Use this for questions about the week ahead, upcoming deadlines, or what's coming up.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "days", Map.of("type", "integer", "description", "Number of days to look ahead. Defaults to 7.", "default", 7)
                    )
                )
            ),
            Map.of(
                "name", "find_free_time",
                "description", "Find available time slots on a specific date. Use this when the user asks when they're free, wants to schedule something, or needs to find open time. Returns free slots that are at least the requested duration.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "date", Map.of("type", "string", "description", "Date to check in ISO format (yyyy-MM-dd). Defaults to today."),
                        "duration_minutes", Map.of("type", "integer", "description", "Minimum free slot duration in minutes. Defaults to 60.", "default", 60),
                        "day_start", Map.of("type", "string", "description", "Working day start time (HH:mm). Defaults to 08:00.", "default", "08:00"),
                        "day_end", Map.of("type", "string", "description", "Working day end time (HH:mm). Defaults to 17:00.", "default", "17:00")
                    )
                )
            ),
            Map.of(
                "name", "list_calendars",
                "description", "List all calendar feeds being tracked, with their sync status. Use this to understand which calendars are available and when they were last updated.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of()
                )
            ),
            Map.of(
                "name", "get_office_location",
                "description", "Get which office the user will be working from on a given date. Returns the configured location or the day-of-week default. Use this when the user asks where they're working from or their office schedule.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "date", Map.of("type", "string", "description", "Date to check in ISO format (yyyy-MM-dd). Defaults to today."),
                        "days", Map.of("type", "integer", "description", "Number of days to show. Defaults to 1. Use 5 for the work week.", "default", 1)
                    )
                )
            ),
            Map.of(
                "name", "set_office_location",
                "description", "Set which office the user will be at on a specific date. Use list_calendars or get_office_location to see valid locations. Use this when the user says they'll be working from a specific location on a date.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "date", Map.of("type", "string", "description", "Date to set in ISO format (yyyy-MM-dd). Defaults to today."),
                        "location", Map.of("type", "string", "description", "Office location name (as configured in the system)")
                    ),
                    "required", List.of("location")
                )
            )
        );
    }
}
