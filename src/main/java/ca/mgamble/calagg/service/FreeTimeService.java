package ca.mgamble.calagg.service;

import ca.mgamble.calagg.config.AppConfig;
import ca.mgamble.calagg.model.CalendarEvent;
import ca.mgamble.calagg.model.FreeSlot;
import ca.mgamble.calagg.model.FreeTimeResponse;
import ca.mgamble.calagg.repository.EventRepository;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FreeTimeService {

    private final EventRepository eventRepository;
    private final AppConfig config;

    public FreeTimeService(EventRepository eventRepository, AppConfig config) {
        this.eventRepository = eventRepository;
        this.config = config;
    }

    public FreeTimeResponse findFreeSlots(LocalDate date, int durationMinutes, LocalTime dayStart, LocalTime dayEnd) {
        if (durationMinutes <= 0) durationMinutes = 30;

        // Query events for the entire day (in UTC)
        ZoneId displayZone = ZoneId.of(config.getDisplayTimezone());
        LocalDateTime utcFrom = date.atStartOfDay().atZone(displayZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime utcTo = date.plusDays(1).atStartOfDay().atZone(displayZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        List<CalendarEvent> events = eventRepository.findByDateRange(utcFrom, utcTo, null, null, null, false, 1000, 0);

        // Convert event times to display timezone
        for (CalendarEvent event : events) {
            event.setDtstart(event.getDtstart().atZone(ZoneOffset.UTC).withZoneSameInstant(displayZone).toLocalDateTime());
            event.setDtend(event.getDtend().atZone(ZoneOffset.UTC).withZoneSameInstant(displayZone).toLocalDateTime());
        }

        // Separate all-day events
        List<String> allDayEvents = new ArrayList<>();
        List<CalendarEvent> timedEvents = new ArrayList<>();
        for (CalendarEvent event : events) {
            if (event.isHidden()) continue;
            if (event.isAllDay()) {
                if (event.getSummary() != null) allDayEvents.add(event.getSummary());
            } else {
                timedEvents.add(event);
            }
        }

        // Build busy blocks from timed events
        List<long[]> busyBlocks = buildBusyBlocks(timedEvents, date, dayStart, dayEnd);

        // Compute free slots from gaps between busy blocks
        List<FreeSlot> freeSlots = computeFreeSlots(busyBlocks, dayStart, dayEnd, durationMinutes);

        int totalFree = freeSlots.stream().mapToInt(FreeSlot::durationMinutes).sum();

        return new FreeTimeResponse(date, dayStart, dayEnd, freeSlots, totalFree, allDayEvents);
    }

    private List<long[]> buildBusyBlocks(List<CalendarEvent> events, LocalDate date, LocalTime dayStart, LocalTime dayEnd) {
        long dayStartMin = dayStart.getHour() * 60L + dayStart.getMinute();
        long dayEndMin = dayEnd.getHour() * 60L + dayEnd.getMinute();

        // Convert events to minute-of-day intervals, clamped to [dayStart, dayEnd]
        List<long[]> intervals = new ArrayList<>();
        for (CalendarEvent event : events) {
            LocalTime evStart = event.getDtstart().toLocalDate().equals(date)
                    ? event.getDtstart().toLocalTime()
                    : LocalTime.MIN;
            LocalTime evEnd = event.getDtend().toLocalDate().equals(date)
                    ? event.getDtend().toLocalTime()
                    : LocalTime.MAX;

            long startMin = Math.max(evStart.getHour() * 60L + evStart.getMinute(), dayStartMin);
            long endMin = Math.min(evEnd.getHour() * 60L + evEnd.getMinute(), dayEndMin);

            if (endMin > startMin) {
                intervals.add(new long[]{startMin, endMin});
            }
        }

        // Sort by start time
        intervals.sort(Comparator.comparingLong(a -> a[0]));

        // Merge overlapping intervals
        List<long[]> merged = new ArrayList<>();
        for (long[] interval : intervals) {
            if (merged.isEmpty() || merged.getLast()[1] < interval[0]) {
                merged.add(new long[]{interval[0], interval[1]});
            } else {
                merged.getLast()[1] = Math.max(merged.getLast()[1], interval[1]);
            }
        }

        return merged;
    }

    private List<FreeSlot> computeFreeSlots(List<long[]> busyBlocks, LocalTime dayStart, LocalTime dayEnd, int minDuration) {
        long dayStartMin = dayStart.getHour() * 60L + dayStart.getMinute();
        long dayEndMin = dayEnd.getHour() * 60L + dayEnd.getMinute();

        List<FreeSlot> slots = new ArrayList<>();
        long cursor = dayStartMin;

        for (long[] block : busyBlocks) {
            if (block[0] > cursor) {
                int gap = (int) (block[0] - cursor);
                if (gap >= minDuration) {
                    slots.add(new FreeSlot(
                            LocalTime.of((int) (cursor / 60), (int) (cursor % 60)),
                            LocalTime.of((int) (block[0] / 60), (int) (block[0] % 60)),
                            gap
                    ));
                }
            }
            cursor = Math.max(cursor, block[1]);
        }

        // Trailing free time after last busy block
        if (cursor < dayEndMin) {
            int gap = (int) (dayEndMin - cursor);
            if (gap >= minDuration) {
                slots.add(new FreeSlot(
                        LocalTime.of((int) (cursor / 60), (int) (cursor % 60)),
                        dayEnd,
                        gap
                ));
            }
        }

        return slots;
    }
}
