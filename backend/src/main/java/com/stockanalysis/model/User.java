package com.stockanalysis.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User — JPA entity stored in H2 in-memory database.
 *
 * Stores credentials (BCrypt hashed password — never plaintext) and
 * the user's preferred market (NASDAQ or FTSE) which pre-selects
 * the stock picker on the dashboard.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique username — used as the JWT subject claim */
    @Column(unique = true, nullable = false, length = 20)
    @NotBlank
    private String username;

    /** Email address — must be unique per user */
    @Column(unique = true, nullable = false, length = 100)
    @Email
    @NotBlank
    private String email;

    /**
     * BCrypt hashed password — NEVER store plaintext.
     * BCrypt output is always 60 characters.
     */
    @Column(nullable = false, length = 60)
    @NotBlank
    private String password;

    /**
     * Preferred market: "NASDAQ" or "FTSE"
     * Determines which stock list appears first on the dashboard.
     */
    @Column(nullable = false, length = 6)
    @Builder.Default
    private String preferredMarket = "NASDAQ";
}
