package com.alphatracker.api.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// Seeds the demo account on boot if it does not exist yet, so the first visitor never waits for it.
// Idempotent, and a failure is logged rather than stopping the app from starting.
@Component
@RequiredArgsConstructor
public class DemoStartupSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoStartupSeeder.class);

    private final DemoSeedService seedService;
    private final DemoProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            seedService.ensureSeeded();
        } catch (RuntimeException e) {
            log.error("Demo seed on startup failed; POST /auth/demo will retry on first use", e);
        }
    }
}
