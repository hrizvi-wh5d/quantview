package com.stockanalysis.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Data Transfer Objects (DTOs) for authentication API.
 * These are the JSON shapes the frontend sends and receives.
 * Using inner classes to keep auth-related DTOs together.
 */
public class AuthDtos {

    // ── REQUEST BODIES ────────────────────────────────────────────────────────

    /**
     * LoginRequest — sent by frontend on POST /api/auth/login
     * { "username": "alice", "password": "secret123" }
     */
    @Data
    public static class LoginRequest {
        @NotBlank(message = "Username is required")
        private String username;

        @NotBlank(message = "Password is required")
        private String password;
    }

    /**
     * RegisterRequest — sent by frontend on POST /api/auth/register
     * { "username": "alice", "email": "alice@example.com",
     *   "password": "secret123", "preferredMarket": "NASDAQ" }
     */
    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be 3-20 characters")
        private String username;

        @Email(message = "Must be a valid email address")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        /** "NASDAQ" or "FTSE" — defaults to NASDAQ if not provided */
        private String preferredMarket = "NASDAQ";
    }

    // ── RESPONSE BODIES ───────────────────────────────────────────────────────

    /**
     * JwtResponse — returned on successful login
     * { "token": "eyJhbGci...", "username": "alice",
     *   "email": "alice@example.com", "preferredMarket": "NASDAQ" }
     *
     * The frontend stores the token in localStorage and sends it as:
     *   Authorization: Bearer eyJhbGci...
     */
    @Data
    public static class JwtResponse {
        private final String token;
        private final String type = "Bearer";  // Always Bearer for JWT
        private final String username;
        private final String email;
        private final String preferredMarket;

        public JwtResponse(String token, String username, String email, String preferredMarket) {
            this.token = token;
            this.username = username;
            this.email = email;
            this.preferredMarket = preferredMarket;
        }
    }

    /**
     * MessageResponse — simple message for success/error responses
     * { "message": "User registered successfully!" }
     */
    @Data
    public static class MessageResponse {
        private final String message;

        public MessageResponse(String message) {
            this.message = message;
        }
    }
}
