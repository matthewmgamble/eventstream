# EventStream API Reference

Base URL: `http://localhost:8080`

All JSON endpoints return `application/json` by default. Many event endpoints also support `Accept: text/plain` for a human/LLM-friendly text agenda format.

---

## Health

### GET `/api/v1/health`

Health check for monitoring.

**Response:**

```json
{
  "status": "healthy",
  "feeds": 3,
  "feedErrors": 0,
  "timezone": "America/Toronto"
}
```

`status` is `"healthy"` when all feeds are OK, `"degraded"` when any feed has errors.

---

## Feeds

### GET `/api/v1/feeds`

List all configured calendar feeds with sync status.

**Response:**

```json
{
  "feeds": [
    {
      "id": 1,
      "name": "Work Calendar",
      "color": "#f73b3b",
      "enabled": true,
      "lastSynced": "2026-03-15T12:51:05",
      "lastStatus": "OK",
      "lastError": null
    }
  ]
}
```

### POST `/api/v1/feeds/sync`

Trigger an immediate sync of all enabled feeds. Sync runs asynchronously.

**Response:**

```json
{
  "ok": true,
  "message": "Sync triggered"
}
```

---

## Events

### GET `/api/v1/events`

Query events with filtering, pagination, and search.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `from` | ISO date or datetime | today | Start of date range |
| `to` | ISO date or datetime | from + 30 days | End of date range |
| `feed_id` | comma-separated longs | all | Filter to specific feed(s) |
| `q` | string | none | Text search across summary, description, location |
| `all_day` | boolean | none | Filter by all-day events |
| `include_hidden` | boolean | false | Include hidden events |
| `limit` | integer | 200 | Max results |
| `offset` | integer | 0 | Pagination offset |

**Response:**

```json
{
  "events": [
    {
      "id": 42,
      "feed": { "id": 1, "name": "Work Calendar", "color": "#f73b3b" },
      "uid": "abc123@google.com",
      "summary": "Team Standup",
      "description": "Daily sync",
      "location": "Microsoft Teams Meeting",
      "start": "2026-03-16T09:00",
      "end": "2026-03-16T09:30",
      "allDay": false,
      "status": "CONFIRMED",
      "hidden": false,
      "notes": null
    }
  ],
  "total": 87,
  "limit": 200,
  "offset": 0
}
```

**Text format:** Send `Accept: text/plain` header to receive a plain text agenda grouped by day, with conflict detection.

### GET `/api/v1/events/today`

Convenience endpoint for today's events. Accepts `include_hidden` query parameter.

Supports `Accept: text/plain`.

### GET `/api/v1/events/upcoming`

Events for the next N days.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `days` | integer | 7 | Number of days ahead |
| `include_hidden` | boolean | false | Include hidden events |

Supports `Accept: text/plain`.

### GET `/api/v1/events/summary`

Returns a plain text agenda optimized for AI agent consumption. Always returns `text/plain`.

Includes events grouped by day, tentative indicators, feed names, locations, notes, and a conflicts section at the end.

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `days` | integer | 7 | Number of days ahead |
| `include_hidden` | boolean | false | Include hidden events |

**Example response:**

```
## Monday, March 16, 2026
- [8:45 AM - 9:00 AM] Kalli/Matt sync (tentative) [Work Calendar] @ Microsoft Teams Meeting
- [9:00 AM - 9:30 AM] Leadership meeting [Work Calendar] @ Microsoft Teams Meeting
- [1:10 PM - 4:10 PM] Blue Jays @ Marlins [Toronto Blue Jays]

## Conflicts
- 'Meeting A' (1:00 PM) overlaps with 'Meeting B' (1:30 PM)
```

### GET `/api/v1/events/{id}`

Get a single event by ID.

**Response:** Single event object (same shape as items in the events array above).

Returns `404` with `{"error": "Event not found"}` if not found.

---

## Event Actions

### POST `/api/v1/events/{id}/hide`

Toggle the hidden flag on an event. Hidden events are excluded from queries by default and from downstream consumers.

**Response:**

```json
{
  "ok": true
}
```

### POST `/api/v1/events/{id}/notes`

Add or update a note on an event. Accepts either a JSON body or plain text.

**JSON body:**

```json
{
  "notes": "Bring the Q4 report"
}
```

**Plain text body:**

```
Bring the Q4 report
```

Send an empty string or blank to clear the note.

**Response:**

```json
{
  "ok": true
}
```

---

## Event Status Values

| Status | Meaning |
|--------|---------|
| `CONFIRMED` | Accepted / confirmed event |
| `TENTATIVE` | Not yet accepted, or marked tentative by the calendar provider |
| `CANCELLED` | Declined or cancelled event |

---

## Notes

- All times in JSON responses are in the configured display timezone (see `/api/v1/health` for current timezone).
- Hidden events are excluded by default; pass `include_hidden=true` to include them.
- The `hidden` and `notes` fields are user-set and preserved across syncs.
- Feeds sync automatically on a configurable interval (default: 60 minutes). Use `POST /api/v1/feeds/sync` to trigger an immediate sync.
