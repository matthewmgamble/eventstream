package ca.mgamble.calagg.model;

import java.time.LocalDateTime;

public class CalendarFeed {

    private long id;
    private String name;
    private String url;
    private String color;
    private String authType;
    private String authValue;
    private boolean enabled;
    private LocalDateTime lastSynced;
    private String lastStatus;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }

    public String getAuthValue() { return authValue; }
    public void setAuthValue(String authValue) { this.authValue = authValue; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getLastSynced() { return lastSynced; }
    public void setLastSynced(LocalDateTime lastSynced) { this.lastSynced = lastSynced; }

    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
