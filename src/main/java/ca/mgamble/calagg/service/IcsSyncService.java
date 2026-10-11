package ca.mgamble.calagg.service;

import ca.mgamble.calagg.config.AppConfig;
import ca.mgamble.calagg.model.CalendarEvent;
import ca.mgamble.calagg.model.CalendarFeed;
import ca.mgamble.calagg.repository.EventRepository;
import ca.mgamble.calagg.repository.FeedRepository;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.time.temporal.Temporal;
import java.util.*;

public class IcsSyncService {

    private static final Logger log = LoggerFactory.getLogger(IcsSyncService.class);
    private final FeedRepository feedRepository;
    private final EventRepository eventRepository;
    private final AppConfig config;
    private final HttpClient httpClient;

    public IcsSyncService(FeedRepository feedRepository, EventRepository eventRepository, AppConfig config) {
        this.feedRepository = feedRepository;
        this.eventRepository = eventRepository;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // Enable relaxed parsing for non-standard ICS feeds (Google, Outlook, etc.)
        System.setProperty("ical4j.parsing.relaxed", "true");
        System.setProperty("ical4j.unfolding.relaxed", "true");
        System.setProperty("ical4j.validation.relaxed", "true");
        System.setProperty("ical4j.compatibility.outlook", "true");
        System.setProperty("ical4j.compatibility.notes", "true");
    }

    public void syncAll() {
        List<CalendarFeed> feeds = feedRepository.findEnabled();
        log.info("Starting sync for {} enabled feeds", feeds.size());
        for (CalendarFeed feed : feeds) {
            syncFeed(feed);
        }
        log.info("Sync complete");
    }

    public void syncFeed(CalendarFeed feed) {
        log.info("Syncing feed: {} (id={})", feed.getName(), feed.getId());
        try {
            InputStream icsStream = fetchIcs(feed);
            CalendarBuilder builder = new CalendarBuilder();
            Calendar calendar = builder.build(icsStream);

            List<CalendarEvent> events = expandEvents(calendar, feed);
            log.info("Feed '{}': parsed {} events", feed.getName(), events.size());

            if (!events.isEmpty()) {
                // Build set of current keys for stale detection
                Set<String> currentKeys = new HashSet<>();
                for (CalendarEvent event : events) {
                    String key = event.getUid() + "|" + (event.getRecurrenceId() != null ? event.getRecurrenceId() : "");
                    currentKeys.add(key);
                }

                // Upsert and clean stale
                eventRepository.upsertBatch(events);
                eventRepository.deleteStaleEvents(feed.getId(), currentKeys);
            }

            feedRepository.updateSyncStatus(feed.getId(), "OK", null);
        } catch (Exception e) {
            log.error("Failed to sync feed '{}': {}", feed.getName(), e.getMessage(), e);
            feedRepository.updateSyncStatus(feed.getId(), "ERROR", e.getMessage());
        }
    }

    private InputStream fetchIcs(CalendarFeed feed) throws Exception {
        String url = feed.getUrl();

        // Handle QUERY_PARAM auth
        if ("QUERY_PARAM".equals(feed.getAuthType()) && feed.getAuthValue() != null) {
            String separator = url.contains("?") ? "&" : "?";
            url = url + separator + feed.getAuthValue();
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET();

        // Handle auth headers
        if (feed.getAuthValue() != null) {
            switch (feed.getAuthType()) {
                case "BASIC" -> requestBuilder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(feed.getAuthValue().getBytes()));
                case "BEARER" -> requestBuilder.header("Authorization", "Bearer " + feed.getAuthValue());
            }
        }

        HttpResponse<InputStream> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " fetching " + feed.getUrl());
        }

        return response.body();
    }

    private List<CalendarEvent> expandEvents(Calendar calendar, CalendarFeed feed) {
        List<CalendarEvent> results = new ArrayList<>();
        int expansionMonths = config.getSyncExpansionMonths();

        LocalDateTime windowStart = LocalDateTime.now().minusDays(30);
        LocalDateTime windowEnd = LocalDateTime.now().plusMonths(expansionMonths);

        List<?> components = calendar.getComponents(Component.VEVENT);
        log.info("Feed '{}': found {} VEVENT components", feed.getName(), components.size());

        int errors = 0;
        for (Object component : components) {
            VEvent vevent = (VEvent) component;
            try {
                expandVEvent(vevent, feed, windowStart, windowEnd, results);
            } catch (Exception e) {
                errors++;
                if (errors <= 3) {
                    String summary = vevent.getProperty(Property.SUMMARY)
                            .map(Property::getValue).orElse("unknown");
                    log.warn("Failed to expand event '{}' in feed '{}': {} ({})",
                            summary, feed.getName(), e.getMessage(), e.getClass().getSimpleName());
                }
            }
        }
        if (errors > 3) {
            log.warn("Feed '{}': {} additional events failed to expand", feed.getName(), errors - 3);
        }

        return results;
    }

    private void expandVEvent(VEvent vevent, CalendarFeed feed, LocalDateTime windowStart, LocalDateTime windowEnd, List<CalendarEvent> results) {
        String uid = vevent.getProperty(Property.UID)
                .map(Property::getValue).orElse(UUID.randomUUID().toString());
        String summary = vevent.getProperty(Property.SUMMARY)
                .map(Property::getValue).orElse(null);
        String description = vevent.getProperty(Property.DESCRIPTION)
                .map(Property::getValue).orElse(null);
        String location = vevent.getProperty(Property.LOCATION)
                .map(Property::getValue).orElse(null);
        String status = vevent.getProperty(Property.STATUS)
                .map(Property::getValue).orElse(null);
        // Check Microsoft X-MICROSOFT-CDO-BUSYSTATUS (Exchange/Outlook uses this instead of PARTSTAT)
        if (status == null || "CONFIRMED".equalsIgnoreCase(status)) {
            String busyStatus = vevent.getProperty("X-MICROSOFT-CDO-BUSYSTATUS")
                    .map(Property::getValue).orElse(null);
            if ("TENTATIVE".equalsIgnoreCase(busyStatus)) {
                status = "TENTATIVE";
            } else if ("FREE".equalsIgnoreCase(busyStatus)) {
                status = "TENTATIVE";
            }
        }
        // Also check ATTENDEE PARTSTAT (Google/standard style)
        if (status == null || "CONFIRMED".equalsIgnoreCase(status)) {
            String partStat = extractPartStat(vevent);
            if (partStat != null) {
                status = partStat;
            }
        }
        String organizer = vevent.getProperty(Property.ORGANIZER)
                .map(Property::getValue).orElse(null);
        String rrule = vevent.getProperty(Property.RRULE)
                .map(Property::getValue).orElse(null);

        // Get start date
        Optional<DtStart> dtStartOpt = vevent.getProperty(Property.DTSTART);
        if (dtStartOpt.isEmpty()) return;

        DtStart dtStartProp = dtStartOpt.get();
        Temporal startTemporal = dtStartProp.getDate();
        boolean allDay = startTemporal instanceof LocalDate;
        LocalDateTime startDt = toLocalDateTime(startTemporal);

        // Get end date
        Optional<DtEnd> dtEndOpt = vevent.getProperty(Property.DTEND);
        LocalDateTime endDt;
        if (dtEndOpt.isPresent()) {
            endDt = toLocalDateTime(dtEndOpt.get().getDate());
        } else {
            endDt = allDay ? startDt.plusDays(1) : startDt.plusHours(1);
        }

        // Handle recurring events
        if (rrule != null) {
            try {
                Period<Temporal> window = new Period<>(windowStart, windowEnd);
                var recurrenceSet = vevent.calculateRecurrenceSet(window);

                for (var period : recurrenceSet) {
                    CalendarEvent event = createEvent(feed.getId(), uid, summary, description,
                            location, allDay, status, organizer, rrule);
                    event.setRecurrenceId(period.getStart().toString());
                    event.setDtstart(toLocalDateTime(period.getStart()));
                    event.setDtend(toLocalDateTime(period.getEnd()));
                    results.add(event);
                }
            } catch (Exception e) {
                // Fallback: store the event un-expanded if RRULE expansion fails
                log.warn("RRULE expansion failed for '{}', storing as single event: {}", summary, e.getMessage());
                if (!startDt.isBefore(windowStart) && !startDt.isAfter(windowEnd)) {
                    CalendarEvent event = createEvent(feed.getId(), uid, summary, description,
                            location, allDay, status, organizer, rrule);
                    event.setDtstart(startDt);
                    event.setDtend(endDt);
                    results.add(event);
                }
            }
        } else {
            // Single event — check if it falls within our window
            if (!startDt.isBefore(windowStart) && !startDt.isAfter(windowEnd)) {
                CalendarEvent event = createEvent(feed.getId(), uid, summary, description,
                        location, allDay, status, organizer, null);
                event.setDtstart(startDt);
                event.setDtend(endDt);
                results.add(event);
            }
        }
    }

    private CalendarEvent createEvent(long feedId, String uid, String summary, String description,
                                       String location, boolean allDay, String status, String organizer, String rrule) {
        CalendarEvent event = new CalendarEvent();
        event.setFeedId(feedId);
        event.setUid(uid);
        event.setRecurrenceId("");
        event.setSummary(summary);
        event.setDescription(description);
        event.setLocation(location);
        event.setAllDay(allDay);
        event.setStatus(status);
        event.setOrganizer(organizer);
        event.setRawRrule(rrule);
        return event;
    }

    private String extractPartStat(VEvent vevent) {
        // Look through ATTENDEE properties for PARTSTAT parameter
        // Returns TENTATIVE, DECLINED, NEEDS-ACTION etc. if found on any attendee
        List<? extends Property> attendees = vevent.getProperties(Property.ATTENDEE);
        for (Property attendee : attendees) {
            var partStatParam = attendee.getParameter("PARTSTAT");
            if (partStatParam.isPresent()) {
                String value = partStatParam.get().getValue();
                if ("TENTATIVE".equalsIgnoreCase(value) || "NEEDS-ACTION".equalsIgnoreCase(value)) {
                    return "TENTATIVE";
                } else if ("DECLINED".equalsIgnoreCase(value)) {
                    return "CANCELLED";
                }
            }
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Temporal temporal) {
        return toUtc(temporal, ZoneId.of(config.getDisplayTimezone()));
    }

    /**
     * An ICS date or time as a UTC LocalDateTime, which is how events are stored. An all-day DATE is midnight where the
     * calendar is shown ({@code zone}): stored as UTC midnight, it showed a day early anywhere west of Greenwich. A
     * floating time (no TZID) is local time too, per RFC 5545.
     */
    static LocalDateTime toUtc(Temporal temporal, ZoneId zone) {
        if (temporal instanceof LocalDate ld) {
            return ld.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } else if (temporal instanceof LocalDateTime ldt) {
            return ldt.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } else if (temporal instanceof ZonedDateTime zdt) {
            return zdt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } else if (temporal instanceof OffsetDateTime odt) {
            return odt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } else if (temporal instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        // Fallback: try parsing as ISO instant (e.g., "2018-12-06T15:00Z")
        String str = temporal.toString();
        try {
            return Instant.parse(str).atZone(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception e) {
            if (str.endsWith("Z")) {
                return LocalDateTime.parse(str.substring(0, str.length() - 1));
            }
            throw e;
        }
    }
}
