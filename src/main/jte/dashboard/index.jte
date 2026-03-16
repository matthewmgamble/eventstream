@param java.util.List<ca.mgamble.calagg.model.CalendarFeed> feeds
@param java.util.List<ca.mgamble.calagg.model.CalendarEvent> events
@param java.util.Map<java.time.LocalDate, java.util.List<ca.mgamble.calagg.model.CalendarEvent>> eventsByDate
@param java.util.Map<java.time.LocalDate, String> officeLocations
@param java.util.List<String> officeChoices
@param String timezone
@param boolean showHidden

@template.layout.main(title = "Dashboard - EventStream", content = @`
    <hgroup>
        <h2>Upcoming Events</h2>
        <p>
            Next 14 days &middot; ${events.size()} events &middot; Timezone: ${timezone}
            &middot;
            @if(showHidden)
                <a href="/dashboard">Hide hidden events</a>
            @else
                <a href="/dashboard?showHidden=true">Show hidden events</a>
            @endif
        </p>
    </hgroup>

    @if(feeds.isEmpty())
        <article>
            <p>No calendar feeds configured yet. <a href="/feeds">Add a feed</a> to get started.</p>
        </article>
    @elseif(events.isEmpty())
        <article>
            <p>No upcoming events found. Your feeds may still be syncing.</p>
        </article>
    @else
        @for(var entry : eventsByDate.entrySet())
            !{var date = entry.getKey();}
            !{var location = officeLocations.getOrDefault(date, "Home");}
            !{var dateStr = date.toString();}
            !{var locClass = "office-" + location.toLowerCase().replace(" ", "");}
            <div class="day-header" id="day-${dateStr}">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <span>
                        ${date.getDayOfWeek().toString().substring(0, 1) + date.getDayOfWeek().toString().substring(1).toLowerCase()}, ${date.getMonth().toString().substring(0, 1) + date.getMonth().toString().substring(1).toLowerCase()} ${date.getDayOfMonth()}, ${date.getYear()}
                    </span>
                    <div style="display: flex; align-items: center; gap: 0.4rem;">
                        <span class="office-badge ${locClass}" id="badge-${dateStr}">${location}</span>
                        <select onchange="setOffice('${dateStr}', this)" style="margin: 0; padding: 0.15rem 0.3rem; font-size: 0.8rem; width: auto;">
                            <option value="" disabled selected>Change...</option>
                            @for(var choice : officeChoices)
                                @if(choice.equals(location))
                                    <option value="${choice}" disabled>${choice}</option>
                                @else
                                    <option value="${choice}">${choice}</option>
                                @endif
                            @endfor
                        </select>
                    </div>
                </div>
            </div>
            @for(var event : entry.getValue())
                <div class="event-card${event.isHidden() ? " event-hidden" : ""}" style="--event-color: ${event.getFeedColor() != null ? event.getFeedColor() : "#3B82F6"}">
                    <div style="display: flex; justify-content: space-between; align-items: flex-start;">
                        <div>
                            <span class="feed-dot" style="background-color: ${event.getFeedColor() != null ? event.getFeedColor() : "#3B82F6"}"></span>
                            @if(event.isAllDay())
                                <span class="event-time">All day</span>
                            @else
                                <span class="event-time">${event.getDtstart().toLocalTime().toString()} - ${event.getDtend().toLocalTime().toString()}</span>
                            @endif
                            &nbsp; <strong>${event.getSummary()}</strong>
                            @if("TENTATIVE".equalsIgnoreCase(event.getStatus()))
                                <span class="event-tentative">tentative</span>
                            @endif
                            @if("CANCELLED".equalsIgnoreCase(event.getStatus()))
                                <span class="event-cancelled">cancelled</span>
                            @endif
                            @if(event.getLocation() != null && !event.getLocation().isBlank())
                                &nbsp; <small>${event.getLocation()}</small>
                            @endif
                            @if(event.getNotes() != null && !event.getNotes().isBlank())
                                <br><small class="event-note">${event.getNotes()}</small>
                            @endif
                        </div>
                        <div class="event-actions">
                            <button class="outline event-action-btn" onclick="toggleHide(${event.getId()}, this)" title="${event.isHidden() ? "Unhide" : "Hide"}">$unsafe{event.isHidden() ? "&#x1F441;" : "&#x1F6AB;"}</button>
                            <button class="outline event-action-btn" onclick="toggleNotes(${event.getId()})" title="Add note">$unsafe{"&#x270E;"}</button>
                        </div>
                    </div>
                    <div id="notes-${event.getId()}" class="notes-form" style="display: none; margin-top: 0.5rem;">
                        <div style="display: flex; gap: 0.5rem; align-items: center;">
                            <input type="text" id="notes-input-${event.getId()}" value="${event.getNotes() != null ? event.getNotes() : ""}" placeholder="Add a note..." style="margin: 0; padding: 0.25rem 0.5rem; font-size: 0.85rem;">
                            <button class="outline" style="padding: 0.25rem 0.5rem; font-size: 0.85rem; white-space: nowrap;" onclick="saveNotes(${event.getId()})">Save</button>
                        </div>
                    </div>
                </div>
            @endfor
        @endfor
    @endif

    <script>
        function toggleHide(eventId, btn) {
            fetch('/events/' + eventId + '/hide', { method: 'POST' }).then(function() {
                var card = btn.closest('.event-card');
                card.classList.toggle('event-hidden');
                btn.innerHTML = card.classList.contains('event-hidden') ? '\u{1F441}' : '\u{1F6AB}';
                btn.title = card.classList.contains('event-hidden') ? 'Unhide' : 'Hide';
            });
        }

        function toggleNotes(eventId) {
            var el = document.getElementById('notes-' + eventId);
            el.style.display = el.style.display === 'none' ? 'block' : 'none';
            if (el.style.display === 'block') {
                el.querySelector('input').focus();
            }
        }

        function saveNotes(eventId) {
            var input = document.getElementById('notes-input-' + eventId);
            var notes = input.value;
            fetch('/events/' + eventId + '/notes', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'notes=' + encodeURIComponent(notes)
            }).then(function() {
                var card = input.closest('.event-card');
                var noteEl = card.querySelector('.event-note');
                if (notes) {
                    if (noteEl) {
                        noteEl.textContent = notes;
                    } else {
                        var small = document.createElement('small');
                        small.className = 'event-note';
                        small.textContent = notes;
                        var br = document.createElement('br');
                        var contentDiv = card.querySelector('.event-card > div > div');
                        contentDiv.appendChild(br);
                        contentDiv.appendChild(small);
                    }
                } else if (noteEl) {
                    noteEl.previousElementSibling.remove();
                    noteEl.remove();
                }
                document.getElementById('notes-' + eventId).style.display = 'none';
            });
        }

        var locClasses = { 'AJ': 'office-aj', 'Oakville': 'office-oakville', 'Downtown': 'office-downtown', 'Home': 'office-home' };

        function setOffice(dateStr, select) {
            var location = select.value;
            fetch('/office/' + dateStr, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'location=' + encodeURIComponent(location)
            }).then(function() {
                var badge = document.getElementById('badge-' + dateStr);
                badge.textContent = location;
                badge.className = 'office-badge ' + (locClasses[location] || 'office-home');
                // Update disabled states
                Array.from(select.options).forEach(function(opt) {
                    opt.disabled = opt.value === location;
                });
                select.selectedIndex = 0;
            });
        }
    </script>
`)
