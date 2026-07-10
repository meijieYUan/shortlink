package com.shortlink.core.scheduler;

import com.shortlink.core.service.impl.ShortLinkServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for link maintenance.
 * - Expiration scan: every minute, marks expired links as status=0
 * - Recycle bin cleanup: every hour, permanently deletes links in recycle bin > 7 days
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkMaintenanceScheduler {

    private final ShortLinkServiceImpl shortLinkService;

    @Scheduled(cron = "0 * * * * *") // every minute
    public void scanExpired() {
        int count = shortLinkService.expireLinks();
        if (count > 0) {
            log.info("Expiration scan: {} links expired", count);
        }
    }

    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanRecycleBin() {
        int count = shortLinkService.cleanRecycleBin(7);
        if (count > 0) {
            log.info("Recycle bin cleanup: {} links permanently deleted", count);
        }
    }
}