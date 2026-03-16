@param java.util.List<ca.mgamble.calagg.model.CalendarFeed> feeds

@template.layout.main(title = "Feeds - EventStream", content = @`
    <hgroup>
        <h2>Calendar Feeds</h2>
        <p>${feeds.size()} feed${feeds.size() != 1 ? "s" : ""} configured</p>
    </hgroup>

    <article>
        <h3>Add Feed</h3>
        <form method="post" action="/feeds">
            <div class="grid">
                <label>
                    Name
                    <input type="text" name="name" placeholder="Work Calendar" required>
                </label>
                <label>
                    Color
                    <input type="color" name="color" value="#3B82F6">
                </label>
            </div>
            <label>
                ICS URL
                <input type="url" name="url" placeholder="https://calendar.google.com/calendar/ical/..." required>
            </label>
            <div class="grid">
                <label>
                    Auth Type
                    <select name="authType">
                        <option value="NONE">None</option>
                        <option value="BASIC">Basic Auth</option>
                        <option value="BEARER">Bearer Token</option>
                        <option value="QUERY_PARAM">Query Parameter</option>
                    </select>
                </label>
                <label>
                    Auth Value
                    <input type="text" name="authValue" placeholder="Token, user:pass, or key=value">
                </label>
            </div>
            <button type="submit">Add Feed</button>
        </form>
    </article>

    @if(!feeds.isEmpty())
        <table>
            <thead>
                <tr>
                    <th>Feed</th>
                    <th>Status</th>
                    <th>Last Synced</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
                @for(var feed : feeds)
                    <tr>
                        <td>
                            <span class="feed-dot" style="background-color: ${feed.getColor()}"></span>
                            <strong>${feed.getName()}</strong>
                            <br>
                            <small>${feed.getUrl().length() > 60 ? feed.getUrl().substring(0, 60) + "..." : feed.getUrl()}</small>
                            @if(!feed.isEnabled())
                                <br><small><em>Disabled</em></small>
                            @endif
                        </td>
                        <td>
                            @if("OK".equals(feed.getLastStatus()))
                                <span class="status-ok">OK</span>
                            @elseif("ERROR".equals(feed.getLastStatus()))
                                <span class="status-error" title="${feed.getLastError() != null ? feed.getLastError() : ""}">Error</span>
                            @else
                                <span class="status-pending">Pending</span>
                            @endif
                        </td>
                        <td>
                            @if(feed.getLastSynced() != null)
                                ${feed.getLastSynced().toString().replace("T", " ")}
                            @else
                                Never
                            @endif
                        </td>
                        <td>
                            <div style="display: flex; gap: 0.5rem;">
                                <form method="post" action="/feeds/${feed.getId()}/sync" style="margin: 0;">
                                    <button type="submit" class="outline" style="padding: 0.25rem 0.5rem; font-size: 0.85rem;">Sync</button>
                                </form>
                                <form method="post" action="/feeds/${feed.getId()}/toggle" style="margin: 0;">
                                    <button type="submit" class="outline secondary" style="padding: 0.25rem 0.5rem; font-size: 0.85rem;">${feed.isEnabled() ? "Disable" : "Enable"}</button>
                                </form>
                                <form method="post" action="/feeds/${feed.getId()}/delete" style="margin: 0;" onsubmit="return confirm('Delete this feed and all its events?')">
                                    <button type="submit" class="outline" style="padding: 0.25rem 0.5rem; font-size: 0.85rem; color: var(--pico-del-color);">Delete</button>
                                </form>
                            </div>
                        </td>
                    </tr>
                @endfor
            </tbody>
        </table>
    @endif
`)
