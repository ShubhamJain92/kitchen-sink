package com.quickstarts.kitchensink.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Key;
import java.time.Instant;
import java.util.Date;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

    private static Key keyFromServiceSecret() {
        byte[] keyBytes = Decoders.BASE64.decode(JwtService.SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Test
    @DisplayName("generateToken -> extractUsername/validateToken work for the same user")
    void generateAndValidate_ok() {
        String token = jwtService.generateToken("alice@example.com");

        assertEquals("alice@example.com", jwtService.extractUsername(token));

        var user = User.withUsername("alice@example.com").password("x").authorities("ADMIN").build();
        assertTrue(jwtService.validateToken(token, user));
    }

    @Test
    @DisplayName("extractExpiration is within the next ~30 minutes")
    void expirationWindow_about30min() {
        String token = jwtService.generateToken("bob@example.com");

        var exp = jwtService.extractExpiration(token);
        var now = new Date();
        // lower bound: not in the past
        assertTrue(exp.after(now), "Expiration should be in the future");
        // upper bound: <= ~31 minutes from now (a little cushion for test time)
        var max = Date.from(Instant.now().plus(31, MINUTES));
        assertTrue(exp.before(max), "Expiration should be ~30 minutes from now");
    }

    @Test
    @DisplayName("validateToken returns false when username doesn't match")
    void validate_wrongUser_false() {
        String token = jwtService.generateToken("carol@example.com");
        var other = User.withUsername("not-carol@example.com").password("x").authorities("ADMIN").build();

        assertFalse(jwtService.validateToken(token, other));
    }

    @Test
    @DisplayName("validateToken returns false for expired token")
    void validate_expired_false() {
        // Arrange
        Key key = keyFromServiceSecret();
        String token = Jwts.builder()
                .setSubject("dave@example.com")
                .setIssuedAt(Date.from(Instant.now().minus(40, MINUTES)))
                .setExpiration(Date.from(Instant.now().minus(5, MINUTES)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        UserDetails user = User.withUsername("dave@example.com")
                .password("x")
                .authorities("ADMIN")
                .build();

        // Act & Assert
        assertFalse(jwtService.validateToken(token, user));
    }

    @Test
    @DisplayName("extractUsername throws on bad signature")
    void extract_badSignature_throws() {
        // Sign with a DIFFERENT key so signature verification fails
        Key otherKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String forged = Jwts.builder()
                .setSubject("eve@example.com")
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(10, MINUTES)))
                .signWith(otherKey, SignatureAlgorithm.HS256)
                .compact();

        assertThrows(JwtException.class, () -> jwtService.extractUsername(forged));
    }

    @Test
    @DisplayName("extractClaim works generically (subject via Claims)")
    void extractClaim_generic() {
        String token = jwtService.generateToken("frank@example.com");
        String sub = jwtService.extractClaim(token, Claims::getSubject);
        assertEquals("frank@example.com", sub);
    }
}
