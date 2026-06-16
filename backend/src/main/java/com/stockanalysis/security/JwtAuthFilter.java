package com.stockanalysis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthFilter — runs once per HTTP request BEFORE Spring Security processes it.
 *
 * Flow:
 *  1. Extract JWT from "Authorization: Bearer <token>" header
 *  2. Validate the token (signature + expiry)
 *  3. Load the user from database by username in token
 *  4. Set authentication in SecurityContext → request is treated as authenticated
 *
 * This makes the API stateless — no server-side sessions.
 * The token itself carries the identity information.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Step 1: Extract JWT from Authorization header
            String jwt = parseJwt(request);

            if (jwt != null && jwtUtils.validateToken(jwt)) {
                // Step 2: Get username from token payload
                String username = jwtUtils.getUsernameFromToken(jwt);

                // Step 3: Load full UserDetails from database
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Step 4: Create authentication object and set in context
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,                        // credentials not needed after auth
                                userDetails.getAuthorities() // roles/permissions
                        );
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // This marks the request as authenticated for this thread
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        // Continue the filter chain (let the request reach the controller)
        filterChain.doFilter(request, response);
    }

    /**
     * Extract the raw JWT string from the Authorization header.
     * Header format: "Authorization: Bearer eyJhbGci..."
     *
     * @return JWT string without "Bearer " prefix, or null if not present
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); // Remove "Bearer " prefix
        }
        return null;
    }
}
