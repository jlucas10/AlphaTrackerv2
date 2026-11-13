package com.alphatracker.api;

import com.alphatracker.api.auth.AuthenticationRequest;
import com.alphatracker.api.auth.AuthenticationResponse;
import com.alphatracker.api.auth.AuthenticationService;
import com.alphatracker.api.demo.DemoAuthService;
import com.alphatracker.api.demo.DemoProperties;
import com.alphatracker.api.demo.DemoSeedService;
import com.alphatracker.api.security.JwtService;
import com.alphatracker.api.security.RateLimitExceededException;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import com.alphatracker.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Real JwtService (so the token is genuinely signed and parsed back); everything else is mocked.
@ExtendWith(MockitoExtension.class)
public class DemoAuthServiceTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B59";

    @Mock
    private DemoSeedService seedService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;

    private JwtService jwtService;
    private User demoUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        demoUser = User.builder().id(7L).email("demo@alphatracker.app").password("unusable")
                .firstName("Demo").role(Role.USER).demo(true).build();
    }

    private DemoAuthService service(boolean enabled, int perHour) {
        return new DemoAuthService(seedService, jwtService,
                new DemoProperties(enabled, "demo@alphatracker.app", 60, perHour, 1));
    }

    @Test
    @DisplayName("POST /auth/demo logic: issues a valid token for the demo user only, flagged demo, expiring in about an hour")
    void issuesTokenForDemoUserOnly() {
        when(seedService.ensureSeeded()).thenReturn(demoUser);

        AuthenticationResponse response = service(true, 5).login("1.2.3.4");

        String token = response.getToken();
        assertEquals("demo@alphatracker.app", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, demoUser));
        assertEquals(Boolean.TRUE, jwtService.extractClaim(token, c -> c.get("demo", Boolean.class)));

        long msUntilExpiry = jwtService.extractClaim(token, c -> c.getExpiration()).getTime() - new Date().getTime();
        assertTrue(msUntilExpiry > 59 * 60_000L && msUntilExpiry <= 60 * 60_000L,
                "expected ~1h expiry but was " + msUntilExpiry + "ms");

        // The token is for the demo user and nobody else.
        User someoneElse = User.builder().id(8L).email("real@example.com").password("x").firstName("R")
                .role(Role.USER).build();
        assertFalse(jwtService.isTokenValid(token, someoneElse));
    }

    @Test
    @DisplayName("is rate-limited per IP: the request over the cap is rejected, a different IP is not")
    void rateLimitsPerIp() {
        when(seedService.ensureSeeded()).thenReturn(demoUser);
        DemoAuthService service = service(true, 2);

        service.login("1.1.1.1");
        service.login("1.1.1.1");
        assertThrows(RateLimitExceededException.class, () -> service.login("1.1.1.1"));
        assertDoesNotThrow(() -> service.login("2.2.2.2"));
    }

    @Test
    @DisplayName("a rate-limited request never touches the database")
    void rateLimitedRequestSkipsSeeding() {
        DemoAuthService service = service(true, 1);
        when(seedService.ensureSeeded()).thenReturn(demoUser);
        service.login("1.1.1.1");
        clearInvocations(seedService);

        assertThrows(RateLimitExceededException.class, () -> service.login("1.1.1.1"));
        verifyNoInteractions(seedService);
    }

    @Test
    @DisplayName("when the demo is disabled the endpoint answers 503 and issues nothing")
    void disabledDemoIssuesNothing() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service(false, 5).login("1.1.1.1"));
        assertEquals(503, ex.getStatusCode().value());
        verifyNoInteractions(seedService);
    }

    @Test
    @DisplayName("the demo account cannot be logged into with a password, even if the manager accepted one")
    void passwordLoginIsRejectedForDemoUser() {
        AuthenticationService authService = new AuthenticationService(
                userRepository, passwordEncoder, jwtService, authenticationManager);
        when(userRepository.findByEmail("demo@alphatracker.app")).thenReturn(Optional.of(demoUser));

        AuthenticationRequest request = new AuthenticationRequest();
        request.setEmail("demo@alphatracker.app");
        request.setPassword("anything");

        assertThrows(IllegalArgumentException.class, () -> authService.authenticate(request));
    }
}
