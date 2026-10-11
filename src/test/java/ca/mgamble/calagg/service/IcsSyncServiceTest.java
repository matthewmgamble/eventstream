package ca.mgamble.calagg.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IcsSyncServiceTest {

    static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    /** Stored UTC, shown in Toronto: what the API, MCP tools and dashboard all do. */
    static LocalDateTime shown(LocalDateTime utc) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(TORONTO).toLocalDateTime();
    }

    @Test
    void anAllDayEventStaysOnItsOwnDay() {
        // Thanksgiving, Monday 12 October 2026: DTSTART;VALUE=DATE:20261012, DTEND;VALUE=DATE:20261013.
        LocalDateTime start = IcsSyncService.toUtc(LocalDate.of(2026, 10, 12), TORONTO);
        LocalDateTime end = IcsSyncService.toUtc(LocalDate.of(2026, 10, 13), TORONTO);
        assertEquals(LocalDateTime.of(2026, 10, 12, 4, 0), start);
        assertEquals(LocalDateTime.of(2026, 10, 12, 0, 0), shown(start));
        assertEquals(LocalDateTime.of(2026, 10, 13, 0, 0), shown(end));
        // In winter too (EST).
        assertEquals(LocalDateTime.of(2026, 12, 25, 0, 0), shown(IcsSyncService.toUtc(LocalDate.of(2026, 12, 25), TORONTO)));
    }

    @Test
    void timesKeepTheirInstant() {
        ZonedDateTime meeting = ZonedDateTime.of(2026, 10, 13, 9, 0, 0, 0, TORONTO);
        assertEquals(LocalDateTime.of(2026, 10, 13, 13, 0), IcsSyncService.toUtc(meeting, TORONTO));
        assertEquals(LocalDateTime.of(2026, 10, 13, 13, 0), IcsSyncService.toUtc(Instant.parse("2026-10-13T13:00:00Z"), TORONTO));
        assertEquals(LocalDateTime.of(2026, 10, 13, 13, 0),
                IcsSyncService.toUtc(OffsetDateTime.of(2026, 10, 13, 15, 0, 0, 0, ZoneOffset.ofHours(2)), TORONTO));
    }

    @Test
    void aFloatingTimeIsLocal() {
        // DTSTART:20261013T090000 with no TZID: 9 AM wherever the calendar is.
        assertEquals(LocalDateTime.of(2026, 10, 13, 9, 0),
                shown(IcsSyncService.toUtc(LocalDateTime.of(2026, 10, 13, 9, 0), TORONTO)));
    }
}
