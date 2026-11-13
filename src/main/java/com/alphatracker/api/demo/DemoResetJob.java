package com.alphatracker.api.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// The daily trigger. Kept separate from DemoResetService so the reset's @Transactional always goes through
// the Spring proxy, and so the schedule can be switched off without touching the reset itself.
@Component
@RequiredArgsConstructor
public class DemoResetJob {

    private static final Logger log = LoggerFactory.getLogger(DemoResetJob.class);

    private final DemoResetService resetService;
    private final DemoProperties properties;

    @Scheduled(cron = "${application.demo.reset-cron:0 0 4 * * *}", zone = "UTC")
    public void run() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            resetService.reset();
        } catch (RuntimeException e) {
            // Never let an exception escape into the scheduler thread; the next day's run tries again.
            log.error("Scheduled demo reset failed", e);
        }
    }
}
