package com.stockanalysis.controller;

import com.stockanalysis.model.AuthDtos.*;
import com.stockanalysis.model.User;
import com.stockanalysis.repository.UserRepository;
import com.stockanalysis.security.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — handles user registration and login.
 *
 * Endpoints (all public — no JWT required):
 *   POST /api/auth/register  — create a new user account
 *   POST /api/auth/login     — authenticate and receive JWT token
 *
 * Flow for login:
 *   1. Client sends { username, password }
 *   2. AuthenticationManager verifies against BCrypt hash in H2
 *   3. If valid → generate JWT → return to client
 *   4. Client stores JWT → sends on every subsequent request
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    /**
     * POST /api/auth/register
     * Create a new user account.
     *
     * Validations:
     *  - Username must be unique
     *  - Email must be unique
     *  - Password is BCrypt hashed before storage
     *
     * Returns 200 with success message, or 400 with error message.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for username: {}", request.getUsername());

        // Check username not already taken
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Username already taken"));
        }

        // Check email not already registered
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Email already registered"));
        }

        // Validate preferred market
        String market = request.getPreferredMarket();
        if (market == null || (!market.equals("NASDAQ") && !market.equals("FTSE"))) {
            market = "NASDAQ"; // Default to NASDAQ
        }

        // Build and save user — BCrypt hash the password
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // NEVER store plaintext
                .preferredMarket(market)
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {}", request.getUsername());

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    /**
     * POST /api/auth/login
     * Authenticate user and return JWT token.
     *
     * The AuthenticationManager handles:
     *  1. Loading user from H2 via UserDetailsService
     *  2. Verifying password against BCrypt hash
     *  3. Throwing exception if credentials invalid
     *
     * Returns JwtResponse with token + user info, or 401 if credentials wrong.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for username: {}", request.getUsername());

        try {
            // Authenticate — throws exception if credentials invalid
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Store authentication in security context for this request
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Generate JWT token
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String jwt = jwtUtils.generateToken(userDetails);

            // Fetch full user entity for the response (to get email and market preference)
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(); // Can't be null — just authenticated successfully

            log.info("Login successful for: {}", request.getUsername());

            return ResponseEntity.ok(new JwtResponse(
                    jwt,
                    user.getUsername(),
                    user.getEmail(),
                    user.getPreferredMarket()
            ));

        } catch (Exception e) {
            log.warn("Login failed for username: {} — {}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(401)
                    .body(new MessageResponse("Error: Invalid username or password"));
        }
    }
}
