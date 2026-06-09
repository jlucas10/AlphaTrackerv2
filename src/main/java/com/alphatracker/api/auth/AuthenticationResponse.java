package com.alphatracker.api.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// The token payload returned to the frontend so it can authorize future API requests
public class AuthenticationResponse {
    private String token;
}
