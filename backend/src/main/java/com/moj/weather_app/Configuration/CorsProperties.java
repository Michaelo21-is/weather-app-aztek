package com.moj.weather_app.Configuration;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "security.cors")
public record CorsProperties(
        @NotEmpty List<String> allowedOrigins
) {
    public CorsProperties {
        // Credentials (cookies) are allowed, so every origin must be listed explicitly.
        if (allowedOrigins != null && allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("security.cors.allowed-origins must not contain wildcards");
        }
    }
}
