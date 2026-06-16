package com.stockanalysis.security;

import com.stockanalysis.model.User;
import com.stockanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * UserDetailsServiceImpl — bridges Spring Security and our User database.
 *
 * Spring Security calls loadUserByUsername() during:
 *  1. Login: to verify the password against the BCrypt hash
 *  2. JWT filter: to rebuild the authentication object from the token
 *
 * We wrap our User entity in a Spring Security UserDetails object
 * which provides username, password, and authorities (roles).
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Load a user from H2 database by username.
     * Spring Security uses the returned UserDetails to:
     *  - Compare passwords (BCrypt hash check)
     *  - Populate the SecurityContext for the request
     *
     * @param username the username from the login request or JWT token
     * @return UserDetails wrapping our User entity
     * @throws UsernameNotFoundException if no user with that username exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username
                ));

        // Build a Spring Security UserDetails from our User entity
        // No roles system needed for this app — all authenticated users have full access
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())   // BCrypt hash — Spring verifies this
                .authorities("ROLE_USER")        // Simple single role
                .build();
    }
}
