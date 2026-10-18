package com.moj.weather_app.Configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Attributes shared by the JWT and CSRF cookies. */
@Validated
@ConfigurationProperties(prefix = "security.cookie")
public record CookieProperties(
        boolean secure,
        @NotBlank String sameSite
) {
}
