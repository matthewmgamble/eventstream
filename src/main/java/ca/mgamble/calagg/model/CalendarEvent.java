package ca.mgamble.calagg.model;

import java.time.LocalDateTime;

public class CalendarEvent {

    private long id;
    private long feedId;
    private String uid;
    private String recurrenceId;
    private String summary;
    private String description;
    private String location;
    private LocalDateTime dtstart;
    private LocalDateTime dtend;
    private boolean allDay;
    private String status;
    private String organizer;
    private String rawRrule;
    private boolean hidden;
    private String notes;
    private LocalDateTime createdAt;

    // Denormalized fields for API/display (not stored in DB)
    private String feedName;
    private String feedColor;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getFeedId() { return feedId; }
    public void setFeedId(long feedId) { this.feedId = feedId; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getRecurrenceId() { return recurrenceId; }
    public void setRecurrenceId(String recurrenceId) { this.recurrenceId = recurrenceId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDateTime getDtstart() { return dtstart; }
    public void setDtstart(LocalDateTime dtstart) { this.dtstart = dtstart; }

    public LocalDateTime getDtend() { return dtend; }
    public void setDtend(LocalDateTime dtend) { this.dtend = dtend; }

    public boolean isAllDay() { return allDay; }
    public void setAllDay(boolean allDay) { this.allDay = allDay; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }

    public String getRawRrule() { return rawRrule; }
    public void setRawRrule(String rawRrule) { this.rawRrule = rawRrule; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getFeedName() { return feedName; }
    public void setFeedName(String feedName) { this.feedName = feedName; }

    public String getFeedColor() { return feedColor; }
    public void setFeedColor(String feedColor) { this.feedColor = feedColor; }
}
