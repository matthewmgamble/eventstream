package ca.mgamble.calagg.model;

import java.time.LocalTime;

public record FreeSlot(
    LocalTime start,
    LocalTime end,
    int durationMinutes
) {}
