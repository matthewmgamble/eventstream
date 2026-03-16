# EventStream

A self-hosted calendar aggregator that consumes multiple ICS feeds, expands recurring events, and exposes both a web dashboard and a JSON API. Includes an MCP (Model Context Protocol) server so AI assistants like Claude can query your calendar with natural language.

## Features

- **Multi-feed ICS aggregation** — Subscribe to any number of ICS calendar feeds (Google Calendar, Outlook/Exchange, iCloud, etc.)
- **Recurring event expansion** — RRULE, RDATE, and EXDATE are fully expanded using ical4j
- **Web dashboard** — Agenda view with feed color-coding, tentative event indicators, hide/unhide, and per-event notes
- **JSON API** — Full REST API for querying events, feeds, free time, and office locations
- **MCP server** — 7 tools for AI assistant integration (get events, find free time, manage office location, etc.)
- **Event hiding** — Hide events from the API and MCP server directly in the dashboard. Useful for shared calendars where you're subscribed to a family member's schedule — you can see their events for awareness but hide them so they don't clutter your free time calculations or AI assistant responses
- **Per-event notes** — Add notes to any event from the dashboard, visible in the API and to AI assistants
- **Free time finder** — Computes available time slots accounting for all calendars (respects hidden events)
- **Office location tracking** — Configurable office locations with day-of-week defaults and per-day overrides
- **Timezone support** — Events stored in UTC, displayed in a configurable timezone

## Stack

- Java 21, Maven
- [Javalin](https://javalin.io/) — HTTP framework
- [JTE](https://jte.gg/) — Template engine
- MariaDB / MySQL — Database
- [ical4j](https://www.ical4j.org/) — ICS parsing and RRULE expansion
- HikariCP — Connection pooling
- Flyway — Database migrations
- Gson — JSON serialization

## Quick Start

### Prerequisites

- Java 21+
- MariaDB or MySQL
- Maven (for building)

### Setup

1. **Create the database:**
   ```sql
   CREATE DATABASE eventstream CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'eventstream'@'%' IDENTIFIED BY 'your-password';
   GRANT ALL PRIVILEGES ON eventstream.* TO 'eventstream'@'%';
   ```

2. **Configure:**
   ```bash
   cp application.properties.example application.properties
   # Edit application.properties with your database credentials
   ```

3. **Build and run:**
   ```bash
   mvn clean package
   java -jar target/eventstream-1.0-SNAPSHOT.jar
   ```

   The app starts on port 8080. Database tables are created automatically by Flyway on first run.

4. **Add a calendar feed:**
   Open `http://localhost:8080/feeds` and add an ICS feed URL.

### Configuration

All settings can be configured in `application.properties`, overridden by an external file in the working directory, or set via environment variables.

| Property | Env Var | Default | Description |
|----------|---------|---------|-------------|
| `db.url` | `DB_URL` | — | JDBC connection string |
| `db.user` | `DB_USER` | — | Database username |
| `db.password` | `DB_PASSWORD` | — | Database password |
| `server.port` | `SERVER_PORT` | `8080` | HTTP port |
| `display.timezone` | `DISPLAY_TIMEZONE` | System default | Display timezone |
| `sync.interval.minutes` | `SYNC_INTERVAL_MINUTES` | `60` | Sync interval |
| `sync.expansion.months` | `SYNC_EXPANSION_MONTHS` | `6` | RRULE expansion window |
| `prune.retention.days` | `PRUNE_RETENTION_DAYS` | `90` | Delete events older than N days |

### Office Locations

Configure office locations and day-of-week defaults:

```properties
# Available locations
office.locations=HQ,Branch Office,Home

# Default when no day-specific default matches
office.default=Home

# Day-of-week defaults
office.default.MONDAY=HQ
office.default.TUESDAY=HQ
office.default.WEDNESDAY=Branch Office

# Aliases (case-insensitive, comma-separated)
office.aliases.HQ=Headquarters,Main Office
office.aliases.Home=WFH,Remote
```

## API

See [API.md](API.md) for the full API reference.

### Key endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/events` | Query events with filters |
| GET | `/api/v1/events/today` | Today's events |
| GET | `/api/v1/events/upcoming?days=7` | Next N days |
| GET | `/api/v1/events/summary?days=7` | Plain text agenda |
| GET | `/api/v1/events/free-time?date=2026-03-16` | Find free time slots |
| GET | `/api/v1/office?date=2026-03-16&days=5` | Office location schedule |
| GET | `/api/v1/health` | Health check |

All event endpoints support `Accept: text/plain` for LLM-friendly output.

## MCP Integration

EventStream includes an MCP server for AI assistant integration. The server exposes 7 tools:

- `get_events` — Query events by date range, calendar, or search term
- `get_today` — Today's events
- `get_upcoming` — Events for the next N days
- `find_free_time` — Available time slots on a date
- `list_calendars` — Calendar feeds and sync status
- `get_office_location` — Where you're working from
- `set_office_location` — Set office for a date

### Claude Desktop Setup

Install [mcp-remote](https://www.npmjs.com/package/mcp-remote) and add to your Claude Desktop config:

**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "calendar": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://localhost:8080/mcp/sse"
      ]
    }
  }
}
```

If connecting to a remote server over HTTP (non-localhost), add `"--allow-http"` to the args.

Restart Claude Desktop after saving. The calendar tools will appear in the tools list.

### Example Prompts

- "What's on my calendar today?"
- "When am I free for 90 minutes on Friday?"
- "Do I have anything with [person] this week?"
- "Where am I working from on Monday?"
- "I'll be downtown on Wednesday"

## Deployment

### systemd

```ini
[Unit]
Description=EventStream Calendar Aggregator
After=network.target

[Service]
Type=simple
User=eventstream
WorkingDirectory=/opt/eventstream
ExecStart=/usr/bin/java -jar /opt/eventstream/eventstream.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### Reverse Proxy (Caddy)

```
calendar.example.com {
    reverse_proxy localhost:8080
}
```

### Template Files

When deploying, copy the JTE templates alongside the jar:

```
/opt/eventstream/
  eventstream.jar
  application.properties
  jte/
    layout/main.jte
    dashboard/index.jte
    feeds/index.jte
```

## License

MIT — see [LICENSE](LICENSE).
