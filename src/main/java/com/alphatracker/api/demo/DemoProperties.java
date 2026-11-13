package com.alphatracker.api.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// The application.demo.* settings (see application.yml). Plain @Value injection, with the same defaults
// as the yml, so a slice test that does not load the yml still gets a working bean.
@Component
public class DemoProperties {

    private final boolean enabled;
    private final String email;
    private final long tokenTtlMinutes;
    private final int rateLimitPerHour;
    private final int trustedProxyHops;

    public DemoProperties(
            @Value("${application.demo.enabled:true}") boolean enabled,
            @Value("${application.demo.email:demo@alphatracker.app}") String email,
            @Value("${application.demo.token-ttl-minutes:60}") long tokenTtlMinutes,
            @Value("${application.demo.rate-limit-per-hour:20}") int rateLimitPerHour,
            @Value("${application.demo.trusted-proxy-hops:1}") int trustedProxyHops) {
        this.enabled = enabled;
        this.email = email.trim().toLowerCase();
        this.tokenTtlMinutes = tokenTtlMinutes;
        this.rateLimitPerHour = rateLimitPerHour;
        this.trustedProxyHops = trustedProxyHops;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getEmail() {
        return email;
    }

    public long getTokenTtlMinutes() {
        return tokenTtlMinutes;
    }

    public int getRateLimitPerHour() {
        return rateLimitPerHour;
    }

    public int getTrustedProxyHops() {
        return trustedProxyHops;
    }
}
