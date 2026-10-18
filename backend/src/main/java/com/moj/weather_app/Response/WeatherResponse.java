package com.moj.weather_app.Response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Weather for one location. Times are UTC instants; add {@link Location#utcOffsetSeconds()} for local time.
 * {@code hourly} is in 3-hour steps for the next 5 days: the finest/longest forecast on OpenWeather's free plan.
 */
public record WeatherResponse(
        Location location,
        Current current,
        List<Hourly> hourly,
        List<Daily> daily,
        String units,
        Instant fetchedAt
) {
    /** {@code name} and {@code country} can be null, e.g. for coordinates in the open sea. */
    public record Location(String name, String state, String country, double lat, double lon, int utcOffsetSeconds) {
    }

    public record Current(Instant time, double temperature, double feelsLike, int humidity, double windSpeed,
                          Condition condition, Instant sunrise, Instant sunset) {
    }

    /** {@code precipitationProbability} ranges from 0 to 1. */
    public record Hourly(Instant time, double temperature, Condition condition, double precipitationProbability) {
    }

    /** Aggregated from the 3-hour steps of one local day; the first and last day may be partial. */
    public record Daily(LocalDate date, double minTemperature, double maxTemperature, Condition condition,
                        double precipitationProbability) {
    }

    /** {@code main} is the condition group (Rain, Clouds, ...); {@code icon} is an OpenWeather icon code. */
    public record Condition(int id, String main, String description, String icon) {
    }
}
