package com.alphatracker.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                // Disable CSRF (Cross-Site Request Forgery)
                // CSRF protection relies on cookies. Since we are building a stateless REST API
                // that uses JWTs in the headers, we don't need it and can safely disable it.
                .csrf(csrf -> csrf.disable())

                // Configure URL routing permissions
                .authorizeHttpRequests(auth -> auth
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
}