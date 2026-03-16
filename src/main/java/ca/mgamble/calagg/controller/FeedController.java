package ca.mgamble.calagg.controller;

import ca.mgamble.calagg.model.CalendarFeed;
import ca.mgamble.calagg.repository.FeedRepository;
import ca.mgamble.calagg.scheduler.SyncScheduler;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeedController {

    private final FeedRepository feedRepository;
    private final SyncScheduler syncScheduler;

    public FeedController(FeedRepository feedRepository, SyncScheduler syncScheduler) {
        this.feedRepository = feedRepository;
        this.syncScheduler = syncScheduler;
    }

    public void register(Javalin app) {
        app.get("/feeds", this::listFeeds);
        app.post("/feeds", this::addFeed);
        app.post("/feeds/{id}/toggle", this::toggleFeed);
        app.post("/feeds/{id}/delete", this::deleteFeed);
        app.post("/feeds/{id}/sync", this::syncFeed);
    }

    private void listFeeds(Context ctx) {
        List<CalendarFeed> feeds = feedRepository.findAll();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("feeds", feeds);
        ctx.render("feeds/index.jte", model);
    }

    private void addFeed(Context ctx) {
        CalendarFeed feed = new CalendarFeed();
        feed.setName(ctx.formParam("name") != null ? ctx.formParam("name").trim() : "");
        feed.setUrl(ctx.formParam("url") != null ? ctx.formParam("url").trim() : "");
        feed.setColor(ctx.formParam("color") != null ? ctx.formParam("color") : "#3B82F6");
        feed.setAuthType(ctx.formParam("authType") != null ? ctx.formParam("authType") : "NONE");
        feed.setAuthValue(ctx.formParam("authValue"));
        feed.setEnabled(true);

        long id = feedRepository.insert(feed);
        if (id > 0) {
            syncScheduler.syncFeedNow(id);
        }
        ctx.redirect("/feeds");
    }

    private void toggleFeed(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        feedRepository.toggleEnabled(id);
        ctx.redirect("/feeds");
    }

    private void deleteFeed(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        feedRepository.delete(id);
        ctx.redirect("/feeds");
    }

    private void syncFeed(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        syncScheduler.syncFeedNow(id);
        ctx.redirect("/feeds");
    }
}
