package com.stockanalysis.repository;

import com.stockanalysis.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository — Spring Data JPA interface.
 * Spring auto-generates the SQL implementation at runtime.
 * No boilerplate SQL needed — just declare the method signatures.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username — used during JWT authentication.
     * Spring generates: SELECT * FROM users WHERE username = ?
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if username already taken — used during registration.
     * Spring generates: SELECT COUNT(*) > 0 FROM users WHERE username = ?
     */
    boolean existsByUsername(String username);

    /**
     * Check if email already registered — prevents duplicate accounts.
     * Spring generates: SELECT COUNT(*) > 0 FROM users WHERE email = ?
     */
    boolean existsByEmail(String email);
}
