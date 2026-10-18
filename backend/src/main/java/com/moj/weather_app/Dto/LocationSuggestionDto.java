package com.moj.weather_app.Dto;

/** An autocomplete match. {@code country} is the ISO 3166 alpha-2 code expected by GET /api/weather/city. */
public record LocationSuggestionDto(String name, String country) {
}
