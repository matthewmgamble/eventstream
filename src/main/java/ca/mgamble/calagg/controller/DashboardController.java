package ca.mgamble.calagg.controller;

import ca.mgamble.calagg.config.AppConfig;
import ca.mgamble.calagg.model.CalendarEvent;
import ca.mgamble.calagg.model.CalendarFeed;
import ca.mgamble.calagg.config.OfficeConfig;
import ca.mgamble.calagg.repository.EventRepository;
import ca.mgamble.calagg.repository.FeedRepository;
import ca.mgamble.calagg.repository.OfficeLocationRepository;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardController {

    private final FeedRepository feedRepository;
    private final EventRepository eventRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final AppConfig config;

    public DashboardController(FeedRepository feedRepository, EventRepository eventRepository,
                               OfficeLocationRepository officeLocationRepository, AppConfig config) {
        this.feedRepository = feedRepository;
        this.eventRepository = eventRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.config = config;
    }

    public void register(Javalin app) {
        app.get("/", ctx -> ctx.redirect("/dashboard"));
        app.get("/dashboard", this::dashboard);
        app.post("/events/{id}/hide", this::toggleHidden);
        app.post("/events/{id}/notes", this::updateNotes);
        app.post("/office/{date}", this::setOfficeLocation);
    }

    private void dashboard(Context ctx) {
        ZoneId displayZone = ZoneId.of(config.getDisplayTimezone());
        boolean showHidden = "true".equals(ctx.queryParam("showHidden"));
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = from.plusDays(14);
        List<CalendarFeed> feeds = feedRepository.findAll();
        List<CalendarEvent> events = eventRepository.findByDateRange(from, to, null, null, null, !showHidden, 500, 0);

        // Convert UTC times to display timezone
        for (CalendarEvent event : events) {
            event.setDtstart(utcToZone(event.getDtstart(), displayZone));
            event.setDtend(utcToZone(event.getDtend(), displayZone));
        }

        // Group events by date in display timezone
        Map<LocalDate, List<CalendarEvent>> eventsByDate = events.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getDtstart().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Build office location map for all dates in range
        Map<LocalDate, String> officeLocations = new LinkedHashMap<>();
        for (int i = 0; i < 14; i++) {
            LocalDate d = LocalDate.now().plusDays(i);
            officeLocations.put(d, officeLocationRepository.getLocationForDate(d));
        }

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("feeds", feeds);
        model.put("events", events);
        model.put("eventsByDate", eventsByDate);
        model.put("officeLocations", officeLocations);
        model.put("officeChoices", officeLocationRepository.getOfficeConfig().getLocations());
        model.put("timezone", config.getDisplayTimezone());
        model.put("showHidden", showHidden);
        ctx.render("dashboard/index.jte", model);
    }

    private void toggleHidden(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        eventRepository.toggleHidden(id);
        if (isFetch(ctx)) {
            ctx.json(Map.of("ok", true));
        } else {
            ctx.redirect(ctx.header("Referer") != null ? ctx.header("Referer") : "/dashboard");
        }
    }

    private void updateNotes(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        String notes = ctx.formParam("notes");
        eventRepository.updateNotes(id, notes);
        if (isFetch(ctx)) {
            ctx.json(Map.of("ok", true));
        } else {
            ctx.redirect(ctx.header("Referer") != null ? ctx.header("Referer") : "/dashboard");
        }
    }

    private void setOfficeLocation(Context ctx) {
        LocalDate date = LocalDate.parse(ctx.pathParam("date"));
        String location = ctx.formParam("location");
        officeLocationRepository.setLocation(date, location);
        if (isFetch(ctx)) {
            ctx.json(Map.of("ok", true));
        } else {
            ctx.redirect(ctx.header("Referer") != null ? ctx.header("Referer") : "/dashboard");
        }
    }

    private boolean isFetch(Context ctx) {
        String fetchHeader = ctx.header("Sec-Fetch-Mode");
        return "cors".equals(fetchHeader) || "same-origin".equals(fetchHeader);
    }

    private LocalDateTime utcToZone(LocalDateTime utc, ZoneId zone) {
        if (utc == null) return null;
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime();
    }
}
