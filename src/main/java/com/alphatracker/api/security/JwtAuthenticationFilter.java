package com.alphatracker.api.security;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService; // Core Spring interface to load user-specific data

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Extract the Authorization header from the request
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // If the header is missing or doesn't start with "Bearer ", pass it to the next
        // filter.
        // It might be a public endpoint like /register or /login.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract the actual token (skipping "Bearer " which is 7 characters)
        jwt = authHeader.substring(7);

        userEmail = jwtService.extractUsername(jwt);

        // If we found an email, and the user isn't *already* authenticated in this
        // current security context
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Fetch the user from the database to make sure they actually exist
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

            // Use JwtService to check if the token matches the user and isn't expired
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // Spring Security uses this specific object to represent an authenticated user
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

                // Enrich the token with details of the web request (IP, session ID, etc.)
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Update the Security Context. This is crucial.
                // Once this is set, Spring knows the user is logged in for the rest of this
                // request's lifecycle.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Hand off control to the next filter in the chain
        filterChain.doFilter(request, response);
    }
}
