package com.alphatracker.api.user;

// What the frontend may know about the signed-in user: a name to greet them with and the email they
// signed up with. Deliberately a small purpose-built object and NOT the User entity, which carries the
// password hash and role (the same lesson as TradeResponse: an explicit allow-list of fields means a
// column added to User later is not exposed unless someone adds it here on purpose).
public record UserResponse(String firstName, String lastName, String email) {

    public static UserResponse fromEntity(User user) {
        return new UserResponse(user.getFirstName(), user.getLastName(), user.getEmail());
    }
}
