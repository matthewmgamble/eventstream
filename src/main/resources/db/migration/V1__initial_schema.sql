CREATE TABLE calendar_feeds (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    url         TEXT NOT NULL,
    color       VARCHAR(7) DEFAULT '#3B82F6',
    auth_type   ENUM('NONE','BASIC','BEARER','QUERY_PARAM') DEFAULT 'NONE',
    auth_value  TEXT NULL,
    enabled     BOOLEAN DEFAULT TRUE,
    last_synced DATETIME NULL,
    last_status ENUM('OK','ERROR','PENDING') DEFAULT 'PENDING',
    last_error  TEXT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE calendar_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    feed_id         BIGINT NOT NULL,
    uid             VARCHAR(512) NOT NULL,
    recurrence_id   VARCHAR(128) NULL,
    summary         VARCHAR(1024),
    description     TEXT,
    location        VARCHAR(1024),
    dtstart         DATETIME NOT NULL,
    dtend           DATETIME NOT NULL,
    all_day         BOOLEAN DEFAULT FALSE,
    status          VARCHAR(32),
    organizer       VARCHAR(512),
    raw_rrule       TEXT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (feed_id) REFERENCES calendar_feeds(id) ON DELETE CASCADE,
    UNIQUE KEY uq_feed_uid_recurrence (feed_id, uid, recurrence_id),
    INDEX idx_dtstart (dtstart),
    INDEX idx_feed_dtrange (feed_id, dtstart, dtend)
);
