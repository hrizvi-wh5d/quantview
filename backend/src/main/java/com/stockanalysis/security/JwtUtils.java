package com.stockanalysis.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JwtUtils — generates, parses, and validates JSON Web Tokens.
 *
 * JWT Structure:
 *   Header.Payload.Signature
 *   - Header: {"alg":"HS256","typ":"JWT"}
 *   - Payload: {"sub":"username","iat":...,"exp":...}
 *   - Signature: HMACSHA256(base64(header)+"."+base64(payload), secret)
 *
 * The client stores the token (localStorage) and sends it on every request
 * as: Authorization: Bearer <token>
 * The server validates the signature — no session state needed (stateless).
 */
@Component
@Slf4j
public class JwtUtils {

    /**
     * Secret key — must be at least 32 chars for HS256.
     * Injected from application.properties.
     * In production: use environment variable JWT_SECRET.
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Token lifetime in milliseconds (24 hours = 86400000ms) */
    @Value("${jwt.expiration}")
    private int jwtExpirationMs;

    /**
     * Build a signing key from the secret string.
     * Keys.hmacShaKeyFor() ensures the key meets HS256 minimum length.
     */
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Generate a JWT token for an authenticated user.
     *
     * @param userDetails Spring Security user object (username is the subject)
     * @return signed JWT string e.g. "eyJhbGci..."
     */
    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())          // "sub" claim
                .setIssuedAt(new Date())                        // "iat" claim
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs)) // "exp"
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extract the username from a JWT token.
     * Used by the filter to identify which user is making the request.
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * Validate a JWT token — checks:
     * 1. Signature is valid (not tampered with)
     * 2. Token has not expired
     * 3. Token is well-formed
     *
     * @param token the JWT string from the Authorization header
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims empty: {}", e.getMessage());
        }
        return false;
    }
}
