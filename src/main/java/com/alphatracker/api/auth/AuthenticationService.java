package com.alphatracker.api.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.alphatracker.api.security.JwtService;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import com.alphatracker.api.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // Takes the registration DTO, securely creates the user in DB, and returns their first access token.
    public AuthenticationResponse register(RegisterRequest request) {
        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                // Why: Encoding the raw password using the BCrypt bean we set up in ApplicationConfig
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER) // Assigning a default client role
                .build();

        userRepository.save(user);
        
        // Generate a token for the user immediately so they don't have to log in right after signing up
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    // Validates credentials via standard Spring Security authentication manager and spits back a fresh token.
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // If the email or password is wrong, this manager method will instantly throw an exception,
        // preventing unauthorized access before moving to the next line.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // If we reach this line, the user is valid. Now grab them from the database to bake the token.
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));
        
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }
}