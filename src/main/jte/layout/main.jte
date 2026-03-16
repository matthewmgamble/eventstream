@param String title = "EventStream"
@param gg.jte.Content content

<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title}</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css">
    <script src="https://unpkg.com/htmx.org@2.0.4"></script>
    <style>
        :root {
            --pico-font-size: 16px;
        }
        nav .brand {
            font-weight: bold;
            font-size: 1.2rem;
        }
        .feed-dot {
            display: inline-block;
            width: 10px;
            height: 10px;
            border-radius: 50%;
            margin-right: 6px;
        }
        .event-card {
            padding: 0.5rem 0.75rem;
            margin-bottom: 0.25rem;
            border-left: 3px solid var(--event-color, #3B82F6);
            background: var(--pico-card-background-color);
            border-radius: 4px;
        }
        .event-time {
            font-size: 0.85rem;
            color: var(--pico-muted-color);
        }
        .day-header {
            margin-top: 1.5rem;
            margin-bottom: 0.5rem;
            font-weight: 600;
            border-bottom: 1px solid var(--pico-muted-border-color);
            padding-bottom: 0.25rem;
        }
        .event-hidden {
            opacity: 0.45;
        }
        .event-actions {
            display: flex;
            gap: 0.25rem;
            flex-shrink: 0;
        }
        .event-action-btn {
            padding: 0.15rem 0.4rem;
            font-size: 0.8rem;
            margin: 0;
            border: none;
            background: none;
            cursor: pointer;
            line-height: 1;
        }
        .event-note {
            color: var(--pico-primary);
            font-style: italic;
        }
        .event-tentative {
            font-size: 0.75rem;
            color: #f59e0b;
            border: 1px solid #f59e0b;
            border-radius: 3px;
            padding: 0 0.3rem;
            margin-left: 0.4rem;
            vertical-align: middle;
        }
        .event-cancelled {
            font-size: 0.75rem;
            color: #ef4444;
            border: 1px solid #ef4444;
            border-radius: 3px;
            padding: 0 0.3rem;
            margin-left: 0.4rem;
            vertical-align: middle;
            text-decoration: line-through;
        }
        .office-badge {
            font-size: 0.75rem;
            padding: 0.1rem 0.5rem;
            border-radius: 3px;
            font-weight: 600;
            white-space: nowrap;
        }
        .office-aj { background: #dbeafe; color: #1e40af; }
        .office-oakville { background: #dcfce7; color: #166534; }
        .office-downtown { background: #fef3c7; color: #92400e; }
        .office-home { background: #f3f4f6; color: #6b7280; }
        .status-ok { color: #22c55e; }
        .status-error { color: #ef4444; }
        .status-pending { color: #f59e0b; }
    </style>
</head>
<body>
    <nav class="container-fluid">
        <ul>
            <li><a href="/dashboard" class="brand">EventStream</a></li>
        </ul>
        <ul>
            <li><a href="/dashboard">Dashboard</a></li>
            <li><a href="/feeds">Feeds</a></li>
        </ul>
    </nav>
    <main class="container">
        ${content}
    </main>
    <footer class="container">
        <small>EventStream Calendar Aggregator</small>
    </footer>
</body>
</html>
