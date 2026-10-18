package com.moj.weather_app.Service;

import com.moj.weather_app.Configuration.JwtProperties;
import com.moj.weather_app.Enum.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private static final String TOKEN_TYPE_CLAIM = "token_type";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UUID userId, TokenType tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(getExpiration(tokenType))))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies the signature and expiry and checks that the token is of the expected type, so a refresh
     * token is never accepted where an access token is required (and vice versa).
     *
     * @throws JwtException if the token is invalid, expired or of another type
     */
    public Claims validateToken(String token, TokenType expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.name().equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Expected a " + expectedType + " token");
        }
        return claims;
    }

    /**
     * Validates the token and returns the ID of the user it was issued for.
     *
     * @throws JwtException if the token is invalid, expired or of another type
     */
    public UUID extractUserId(String token, TokenType expectedType) {
        String subject = validateToken(token, expectedType).getSubject();
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("Token subject is not a valid user ID");
        }
    }

    public Duration getExpiration(TokenType tokenType) {
        return switch (tokenType) {
            case ACCESS -> properties.accessTokenExpiration();
            case REFRESH -> properties.refreshTokenExpiration();
        };
    }
}
