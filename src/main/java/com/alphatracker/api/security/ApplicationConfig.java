package com.alphatracker.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.alphatracker.api.user.UserRepository; // Assuming this is where your database interface lives

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor // Why: Injects our UserRepository automatically via constructor
public class ApplicationConfig {

    private final UserRepository userRepository;

    // 1. Tell Spring how to find a user in our PostgreSQL database
    @Bean
    public UserDetailsService userDetailsService() {
        // Why: Spring Security's loadUserByUsername method defaults to looking for a "username".
        // For AlphaTracker, we want users to log in with their email. So we override it here.
        return username -> userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }

    // 2. Define the Authentication Engine
    @Bean
    public AuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());        
        authProvider.setPasswordEncoder(passwordEncoder());
        
        return authProvider;
    }

    // 3. Define the Password Hashing Strategy
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Why: Production-grade apps NEVER store raw passwords in a database. 
        // BCrypt automatically salts and hashes passwords to protect data integrity.
        return new BCryptPasswordEncoder();
    }

    // 4. The Master Coordinator for Authentication
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        // Why: This is the manager bean that your login controller will call later 
        // to actually process username/password authentication requests.
        return config.getAuthenticationManager();
    }
}