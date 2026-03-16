package ca.mgamble.calagg.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record FreeTimeResponse(
    LocalDate date,
    LocalTime dayStart,
    LocalTime dayEnd,
    List<FreeSlot> freeSlots,
    int totalFreeMinutes,
    List<String> allDayEvents
) {}
