package com.alphatracker.api.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity // Why: Tells Spring this is our central hub for web security settings
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider; // Why: The engine that verifies user credentials

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enable CORS using the bean below.
                // Without this, the browser's preflight OPTIONS request — which by design
                // carries no Authorization header — hits anyRequest().authenticated() and is
                // rejected with a 403 that has no CORS headers on it. The browser then reports
                // it as a CORS failure and the real request is never sent at all.
                .cors(Customizer.withDefaults())

                // Disable CSRF (Cross-Site Request Forgery)
                // CSRF protection relies on cookies. Since we are building a stateless REST API
                // that uses JWTs in the headers, we don't need it and can safely disable it.
                .csrf(csrf -> csrf.disable())

                // Configure URL routing permissions
                .authorizeHttpRequests(auth -> auth
                        // Let preflight through explicitly. Spring Security's CORS filter already
                        // short-circuits OPTIONS before this point, but stating it here means a
                        // future refactor of the CORS setup cannot silently reintroduce the 403.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Allow anyone to access the auth endpoints (login, register) without a token
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/trades/**").authenticated() // Require valid authentication, no strict
                                                                              // role string check needed
                        // ANY other request to the API must be authenticated
                        .anyRequest().authenticated())

                // Make the session stateless
                // Traditional apps save user state in server memory (sessions). For a scalable
                // backend, we want it completely stateless. Every single request must stand on
                // its own
                // and prove who it is via the JWT.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Set up the authentication engine
                .authenticationProvider(authenticationProvider)

                // Inject your custom JWT filter into the assembly line
                // We tell Spring to execute our JwtAuthenticationFilter BEFORE it runs its own
                // built-in UsernamePasswordAuthenticationFilter. This ensures we catch the
                // token,
                // validate it, and authenticate the user before Spring tries to process
                // standard form login.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Central CORS policy for the whole API.
    // Previously only AuthenticationController carried @CrossOrigin, so login worked
    // while every /api/v1/trades call failed preflight. Configuring it once here means
    // new controllers are covered by default instead of re-hitting that bug.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Pattern rather than a fixed origin: Vite picks 5174, 5175... when 5173 is
        // already taken, and a hardcoded port breaks the moment that happens.
        // DEV ONLY - narrow this to the real deployed origin before shipping.
        config.setAllowedOriginPatterns(List.of("http://localhost:*"));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Cache the preflight for an hour instead of re-asking per request.

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}