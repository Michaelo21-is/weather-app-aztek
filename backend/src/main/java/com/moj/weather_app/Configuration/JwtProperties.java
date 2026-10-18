package com.moj.weather_app.Configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        // HS256 needs a key of at least 256 bits.
        @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters") String secret,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration
) {
    /** Keeps the secret out of logs and error messages. */
    @Override
    public String toString() {
        return "JwtProperties[secret=****, accessTokenExpiration=" + accessTokenExpiration
                + ", refreshTokenExpiration=" + refreshTokenExpiration + "]";
    }
}
