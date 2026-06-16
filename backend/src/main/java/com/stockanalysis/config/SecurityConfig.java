package com.stockanalysis.config;

import com.stockanalysis.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * SecurityConfig — configures Spring Security for stateless JWT authentication.
 *
 * Key decisions:
 *  - STATELESS: no server sessions, every request authenticated via JWT
 *  - CORS: allows GitHub Codespaces wildcard domains + localhost for dev
 *  - Public routes: /api/auth/** (login, register) — no token needed
 *  - Protected routes: everything else requires a valid JWT
 *  - H2 console: frameOptions disabled so the H2 web UI works (uses iframes)
 *  - BCrypt: cost factor 10 (~100ms hash time — prevents brute force)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    /**
     * Main security filter chain.
     * Defines which routes are public vs protected, session policy, and filter order.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── CSRF ──────────────────────────────────────────────────────────
            // Disable CSRF: we use JWT in Authorization header (not cookies)
            // CSRF attacks only work against cookie-based auth
            .csrf(csrf -> csrf.disable())

            // ── CORS ──────────────────────────────────────────────────────────
            // Use our custom CORS config (see corsConfigurationSource below)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── FRAME OPTIONS ─────────────────────────────────────────────────
            // Disable X-Frame-Options so H2 Console iframes work
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))

            // ── ROUTE AUTHORISATION ───────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Public: login and registration don't need a token
                .requestMatchers("/api/auth/**").permitAll()
                // Public: H2 console for debugging
                .requestMatchers("/h2-console/**").permitAll()
                // Everything else requires authentication (valid JWT)
                .anyRequest().authenticated()
            )

            // ── SESSION POLICY ────────────────────────────────────────────────
            // STATELESS: Spring Security never creates or uses HTTP sessions
            // Each request must carry its own JWT token
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ── AUTHENTICATION PROVIDER ───────────────────────────────────────
            // Wire up our BCrypt password encoder and UserDetailsService
            .authenticationProvider(authenticationProvider())

            // ── JWT FILTER ────────────────────────────────────────────────────
            // Run our JWT filter BEFORE Spring's built-in username/password filter
            // This means the SecurityContext is populated before any auth decision
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration — critical for GitHub Codespaces.
     *
     * Codespaces generates dynamic URLs like:
     *   https://abc123-3000.app.github.dev  (React frontend)
     *   https://abc123-8080.app.github.dev  (Spring Boot API)
     *
     * We must allow wildcard subdomains of github.dev and app.github.dev.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allowed origins — supports local dev and Codespaces
        config.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:3000",           // Local React dev server
            "http://localhost:8080",           // Local Spring Boot
            "https://*.app.github.dev",        // Codespaces app subdomain
            "https://*.github.dev"             // Codespaces direct subdomain
        ));

        // Allowed HTTP methods
        config.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // Allowed headers — Authorization carries the JWT token
        config.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin"
        ));

        // Allow credentials (cookies, Authorization header)
        config.setAllowCredentials(true);

        // How long browsers cache preflight OPTIONS response (1 hour)
        config.setMaxAge(3600L);

        // Apply this CORS config to all routes
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Authentication provider — connects BCrypt encoder with UserDetailsService.
     * Spring Security uses this to verify passwords during login.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * BCryptPasswordEncoder — hashes passwords before storing in H2.
     * Cost factor 10 (default) takes ~100ms — makes brute force attacks slow.
     * Even if the H2 database is compromised, raw passwords are never exposed.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager — used by AuthController to authenticate login requests.
     * Spring Boot 3 requires explicit exposure as a @Bean.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig
    ) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
