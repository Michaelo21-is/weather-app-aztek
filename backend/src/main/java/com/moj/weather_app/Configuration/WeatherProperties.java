package com.moj.weather_app.Configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "weather")
public record WeatherProperties(
        // OpenWeather API key; only ever sent server-to-server, never to the frontend.
        @NotBlank String apiKey,
        @NotBlank String openWeatherBaseUrl,
        // OpenWeather geocoding only matches whole names, so prefix autocomplete uses Open-Meteo.
        @NotBlank String autocompleteBaseUrl,
        @NotBlank String units,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration autocompleteTtl,
        @NotNull Duration geocodingTtl
) {
    /** Keeps the API key out of logs and error messages. */
    @Override
    public String toString() {
        return "WeatherProperties[apiKey=****, openWeatherBaseUrl=" + openWeatherBaseUrl
                + ", autocompleteBaseUrl=" + autocompleteBaseUrl + ", units=" + units
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout
                + ", autocompleteTtl=" + autocompleteTtl + ", geocodingTtl=" + geocodingTtl + "]";
    }
}
