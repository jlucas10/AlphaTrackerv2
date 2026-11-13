package com.alphatracker.api;

import com.alphatracker.api.auth.AuthenticationController;
import com.alphatracker.api.auth.AuthenticationResponse;
import com.alphatracker.api.auth.AuthenticationService;
import com.alphatracker.api.demo.DemoAuthService;
import com.alphatracker.api.demo.DemoProperties;
import com.alphatracker.api.security.JwtAuthenticationFilter;
import com.alphatracker.api.security.JwtService;
import com.alphatracker.api.security.RateLimitExceededException;
import com.alphatracker.api.security.SecurityConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The REAL SecurityConfiguration and JwtAuthenticationFilter are loaded (with their collaborators mocked), and
// no user() is supplied: this proves /auth/demo is genuinely public, not just that the controller works.
@WebMvcTest(controllers = AuthenticationController.class)
@Import({ SecurityConfiguration.class, JwtAuthenticationFilter.class })
public class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;
    @MockitoBean
    private DemoAuthService demoAuthService;
    @MockitoBean
    private DemoProperties demoProperties;
    // Collaborators of the real security config / filter.
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /auth/demo needs no credentials and returns a token")
    void demoReturnsToken() throws Exception {
        when(demoProperties.getTrustedProxyHops()).thenReturn(1);
        when(demoAuthService.login("203.0.113.7"))
                .thenReturn(AuthenticationResponse.builder().token("demo.jwt.token").build());

        mockMvc.perform(post("/api/v1/auth/demo")
                        .header("X-Forwarded-For", "6.6.6.6, 203.0.113.7")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("demo.jwt.token"));
    }

    @Test
    @DisplayName("POST /auth/demo answers 429 with Retry-After when the caller's IP is over its limit")
    void demoRateLimited() throws Exception {
        when(demoProperties.getTrustedProxyHops()).thenReturn(1);
        when(demoAuthService.login("203.0.113.7")).thenThrow(new RateLimitExceededException(42));

        mockMvc.perform(post("/api/v1/auth/demo")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.message").exists());
    }
}
