package ca.mgamble.calagg.service;

import ca.mgamble.calagg.config.AppConfig;
import ca.mgamble.calagg.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class PruneService {

    private static final Logger log = LoggerFactory.getLogger(PruneService.class);
    private final EventRepository eventRepository;
    private final AppConfig config;

    public PruneService(EventRepository eventRepository, AppConfig config) {
        this.eventRepository = eventRepository;
        this.config = config;
    }

    public void prune() {
        int retentionDays = config.getPruneRetentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = eventRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Pruned {} events older than {} days", deleted, retentionDays);
        }
    }
}
