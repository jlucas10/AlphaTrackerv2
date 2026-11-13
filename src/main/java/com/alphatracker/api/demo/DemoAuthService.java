package com.alphatracker.api.demo;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alphatracker.api.auth.AuthenticationResponse;
import com.alphatracker.api.security.JwtService;
import com.alphatracker.api.security.SlidingWindowRateLimiter;
import com.alphatracker.api.user.User;

// One-click login: no credentials, a short-lived token for the demo user ONLY. The only identity this can
// ever issue a token for is the configured demo user (and ensureSeeded refuses a non-demo row), so the
// endpoint cannot be used to become anyone else.
@Service
public class DemoAuthService {

    private final DemoSeedService seedService;
    private final JwtService jwtService;
    private final DemoProperties properties;
    private final SlidingWindowRateLimiter limiter;

    public DemoAuthService(DemoSeedService seedService, JwtService jwtService, DemoProperties properties) {
        this.seedService = seedService;
        this.jwtService = jwtService;
        this.properties = properties;
        this.limiter = new SlidingWindowRateLimiter(
                properties.getRateLimitPerHour(), Duration.ofHours(1).toMillis());
    }

    // `clientKey` is the caller's IP (see ClientIp). Throws RateLimitExceededException (429) when over the cap.
    public AuthenticationResponse login(String clientKey) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The demo is currently unavailable.");
        }
        limiter.admit(clientKey);

        User demoUser = seedService.ensureSeeded();
        String token = jwtService.generateToken(
                Map.of("demo", true), demoUser, Duration.ofMinutes(properties.getTokenTtlMinutes()).toMillis());
        return AuthenticationResponse.builder().token(token).build();
    }
}
