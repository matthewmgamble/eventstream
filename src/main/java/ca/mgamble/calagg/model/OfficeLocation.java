package ca.mgamble.calagg.model;

import java.time.LocalDate;

public class OfficeLocation {

    private long id;
    private LocalDate date;
    private String location;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
