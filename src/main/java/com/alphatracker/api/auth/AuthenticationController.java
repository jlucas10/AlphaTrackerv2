package com.alphatracker.api.auth;

import org.springframework.http.ResponseEntity;
import com.alphatracker.api.demo.ClientIp;
import com.alphatracker.api.demo.DemoAuthService;
import com.alphatracker.api.demo.DemoProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth") // Matches the .permitAll() pattern we declared in SecurityConfiguration
@RequiredArgsConstructor
// CORS is configured globally in SecurityConfiguration; the per-controller
// @CrossOrigin was removed so auth and trades share one policy.
public class AuthenticationController {

    private final AuthenticationService service;
    private final DemoAuthService demoAuthService;
    private final DemoProperties demoProperties;

    // Handles POST requests to /api/v1/auth/register
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(service.register(request));
    }

    // Handles POST requests to /api/v1/auth/authenticate
    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request) {
        return ResponseEntity.ok(service.authenticate(request));
    }

    // Handles POST requests to /api/v1/auth/demo: one-click login as the shared demo user. No body, no
    // credentials; rate-limited per client IP inside DemoAuthService.
    @PostMapping("/demo")
    public ResponseEntity<AuthenticationResponse> demo(HttpServletRequest request) {
        String clientIp = ClientIp.resolve(request, demoProperties.getTrustedProxyHops());
        return ResponseEntity.ok(demoAuthService.login(clientIp));
    }
}
