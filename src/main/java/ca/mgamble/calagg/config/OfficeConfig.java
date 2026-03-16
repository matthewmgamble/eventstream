package ca.mgamble.calagg.config;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Configurable office locations and day-of-week defaults.
 *
 * Configuration in application.properties:
 *   office.locations=Office A,Office B,Home
 *   office.aliases.Office A=OA,headquarters
 *   office.aliases.Home=WFH,Remote
 *   office.default=Home
 *   office.default.MONDAY=Office A
 *   office.default.TUESDAY=Office B
 */
public class OfficeConfig {

    private final List<String> locations;
    private final String defaultLocation;
    private final Map<DayOfWeek, String> dayDefaults;
    private final Map<String, String> aliasMap; // lowercase alias -> canonical name

    public OfficeConfig(AppConfig config) {
        // Parse location list
        String locationStr = config.get("office.locations", "Home");
        this.locations = Arrays.stream(locationStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        // Ensure "Home" or some default exists
        this.defaultLocation = config.get("office.default", locations.getLast());

        // Parse day-of-week defaults
        this.dayDefaults = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dow : DayOfWeek.values()) {
            String val = config.get("office.default." + dow.name());
            if (val != null && !val.isBlank()) {
                dayDefaults.put(dow, val.trim());
            }
        }

        // Build alias map: each location name maps to itself (case-insensitive),
        // plus any configured aliases
        this.aliasMap = new LinkedHashMap<>();
        for (String loc : locations) {
            aliasMap.put(loc.toLowerCase(), loc);
        }
        // Parse configured aliases
        for (String loc : locations) {
            String aliases = config.get("office.aliases." + loc);
            if (aliases != null) {
                for (String alias : aliases.split(",")) {
                    String trimmed = alias.trim();
                    if (!trimmed.isEmpty()) {
                        aliasMap.put(trimmed.toLowerCase(), loc);
                    }
                }
            }
        }
    }

    public List<String> getLocations() {
        return locations;
    }

    public String getDefaultLocation() {
        return defaultLocation;
    }

    public String getDefaultForDay(DayOfWeek dow) {
        return dayDefaults.getOrDefault(dow, defaultLocation);
    }

    public String getDefaultForDate(java.time.LocalDate date) {
        return getDefaultForDay(date.getDayOfWeek());
    }

    /**
     * Normalize a user-provided location string to its canonical name.
     * Returns the input as-is if no match is found.
     */
    public String normalize(String input) {
        if (input == null || input.isBlank()) return defaultLocation;
        String canonical = aliasMap.get(input.toLowerCase().trim());
        return canonical != null ? canonical : input.trim();
    }

    public boolean isValid(String location) {
        return locations.contains(normalize(location));
    }
}
