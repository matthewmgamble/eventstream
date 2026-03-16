package ca.mgamble.calagg.scheduler;

import ca.mgamble.calagg.config.AppConfig;
import ca.mgamble.calagg.model.CalendarFeed;
import ca.mgamble.calagg.repository.FeedRepository;
import ca.mgamble.calagg.service.IcsSyncService;
import ca.mgamble.calagg.service.PruneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);
    private final IcsSyncService syncService;
    private final PruneService pruneService;
    private final FeedRepository feedRepository;
    private final AppConfig config;
    private final ScheduledExecutorService executor;

    public SyncScheduler(IcsSyncService syncService, PruneService pruneService, FeedRepository feedRepository, AppConfig config) {
        this.syncService = syncService;
        this.pruneService = pruneService;
        this.feedRepository = feedRepository;
        this.config = config;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sync-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        int intervalMinutes = config.getSyncIntervalMinutes();

        // Initial sync after a short delay to let the app finish starting
        executor.schedule(this::runSyncCycle, 5, TimeUnit.SECONDS);

        // Recurring sync
        executor.scheduleAtFixedRate(this::runSyncCycle, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);

        log.info("Sync scheduler started: initial sync in 5s, then every {} minutes", intervalMinutes);
    }

    private void runSyncCycle() {
        try {
            syncService.syncAll();
            pruneService.prune();
        } catch (Exception e) {
            log.error("Sync cycle failed", e);
        }
    }

    public void triggerNow() {
        executor.submit(this::runSyncCycle);
    }

    public void syncFeedNow(long feedId) {
        executor.submit(() -> {
            try {
                CalendarFeed feed = feedRepository.findById(feedId).orElse(null);
                if (feed != null) {
                    syncService.syncFeed(feed);
                } else {
                    log.warn("Cannot sync feed {}: not found", feedId);
                }
            } catch (Exception e) {
                log.error("Manual sync for feed {} failed", feedId, e);
            }
        });
    }

    public void stop() {
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
