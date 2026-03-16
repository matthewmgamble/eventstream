-- Drop the unique key first so we can clean up
ALTER TABLE calendar_events DROP INDEX uq_feed_uid_recurrence;

-- Remove duplicates: keep the row with the lowest id for each (feed_id, uid, recurrence_id) group
-- Handle NULL recurrence_id rows
DELETE e1 FROM calendar_events e1
INNER JOIN calendar_events e2
ON e1.feed_id = e2.feed_id AND e1.uid = e2.uid
AND (e1.recurrence_id IS NULL AND e2.recurrence_id IS NULL)
AND e1.id > e2.id;

-- Handle non-NULL recurrence_id rows
DELETE e1 FROM calendar_events e1
INNER JOIN calendar_events e2
ON e1.feed_id = e2.feed_id AND e1.uid = e2.uid
AND e1.recurrence_id = e2.recurrence_id
AND e1.id > e2.id;

-- Now convert NULLs to empty string
UPDATE calendar_events SET recurrence_id = '' WHERE recurrence_id IS NULL;

-- Make the column NOT NULL with default empty string
ALTER TABLE calendar_events MODIFY recurrence_id VARCHAR(128) NOT NULL DEFAULT '';

-- Re-add the unique key
ALTER TABLE calendar_events ADD UNIQUE KEY uq_feed_uid_recurrence (feed_id, uid, recurrence_id);
