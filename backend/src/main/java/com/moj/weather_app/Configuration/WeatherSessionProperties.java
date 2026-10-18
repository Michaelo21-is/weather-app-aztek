package com.moj.weather_app.Configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** The weather session cookie. Its lifetime is independent of (and much longer than) the hourly weather cache. */
@Validated
@ConfigurationProperties(prefix = "weather.session")
public record WeatherSessionProperties(
        @NotBlank String cookieName,
        @NotNull Duration maxAge
) {
}
