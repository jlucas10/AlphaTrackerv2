package com.alphatracker.api.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Captures the registration form data sent from the frontend/Postman
public class RegisterRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String password;

}
