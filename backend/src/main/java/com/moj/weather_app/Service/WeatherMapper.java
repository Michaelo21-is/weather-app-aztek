package com.moj.weather_app.Service;

import com.moj.weather_app.Client.OpenWeatherClient.Condition;
import com.moj.weather_app.Client.OpenWeatherClient.CurrentWeather;
import com.moj.weather_app.Client.OpenWeatherClient.Forecast;
import com.moj.weather_app.Client.OpenWeatherClient.ForecastItem;
import com.moj.weather_app.Response.WeatherResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Converts OpenWeather responses into our {@link WeatherResponse}. */
final class WeatherMapper {
    // The 3-hour step closest to local noon represents a day's weather (and gets a daytime icon).
    private static final LocalTime REPRESENTATIVE_TIME = LocalTime.NOON;

    private WeatherMapper() {
    }

    static WeatherResponse toResponse(CurrentWeather current, Forecast forecast, WeatherResponse.Location location,
                                      String units, Instant fetchedAt) {
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(location.utcOffsetSeconds());
        List<ForecastItem> items = forecast.list() == null ? List.of() : forecast.list();
        return new WeatherResponse(location, current(current), hourly(items), daily(items, offset), units, fetchedAt);
    }

    /** The city name comes from the weather response itself (OpenWeather's built-in reverse lookup). */
    static WeatherResponse.Location coordinatesLocation(CurrentWeather current, Forecast forecast,
                                                        double lat, double lon) {
        String name = blankToNull(current.name());
        String country = current.sys() == null ? null : blankToNull(current.sys().country());
        if (name == null && forecast.city() != null) {
            name = blankToNull(forecast.city().name());
            country = blankToNull(forecast.city().country());
        }
        return new WeatherResponse.Location(name, null, country, lat, lon, utcOffsetSeconds(current, forecast));
    }

    static int utcOffsetSeconds(CurrentWeather current, Forecast forecast) {
        if (current.timezone() != null) {
            return current.timezone();
        }
        return forecast.city() != null && forecast.city().timezone() != null ? forecast.city().timezone() : 0;
    }

    private static WeatherResponse.Current current(CurrentWeather current) {
        return new WeatherResponse.Current(
                Instant.ofEpochSecond(current.dt()),
                current.main().temp(),
                current.main().feelsLike(),
                current.main().humidity(),
                current.wind() == null ? 0 : current.wind().speed(),
                condition(current.weather()),
                current.sys() == null ? null : toInstant(current.sys().sunrise()),
                current.sys() == null ? null : toInstant(current.sys().sunset()));
    }

    private static List<WeatherResponse.Hourly> hourly(List<ForecastItem> items) {
        return items.stream()
                .map(item -> new WeatherResponse.Hourly(
                        Instant.ofEpochSecond(item.dt()),
                        item.main().temp(),
                        condition(item.weather()),
                        precipitationProbability(item)))
                .toList();
    }

    private static List<WeatherResponse.Daily> daily(List<ForecastItem> items, ZoneOffset offset) {
        Map<LocalDate, List<ForecastItem>> byLocalDate = items.stream().collect(Collectors.groupingBy(
                item -> LocalDate.ofInstant(Instant.ofEpochSecond(item.dt()), offset),
                LinkedHashMap::new,
                Collectors.toList()));
        return byLocalDate.entrySet().stream()
                .map(day -> daily(day.getKey(), day.getValue(), offset))
                .toList();
    }

    private static WeatherResponse.Daily daily(LocalDate date, List<ForecastItem> items, ZoneOffset offset) {
        ForecastItem representative = items.stream()
                .min(Comparator.comparingLong(item -> Math.abs(
                        localTime(item, offset).toSecondOfDay() - REPRESENTATIVE_TIME.toSecondOfDay())))
                .orElseThrow();
        return new WeatherResponse.Daily(
                date,
                items.stream().mapToDouble(item -> item.main().tempMin()).min().orElseThrow(),
                items.stream().mapToDouble(item -> item.main().tempMax()).max().orElseThrow(),
                condition(representative.weather()),
                items.stream().mapToDouble(WeatherMapper::precipitationProbability).max().orElseThrow());
    }

    private static LocalTime localTime(ForecastItem item, ZoneOffset offset) {
        return LocalTime.ofInstant(Instant.ofEpochSecond(item.dt()), offset);
    }

    private static double precipitationProbability(ForecastItem item) {
        return item.pop() == null ? 0 : item.pop();
    }

    /** OpenWeather lists the primary condition first. */
    private static WeatherResponse.Condition condition(List<Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        Condition primary = conditions.get(0);
        return new WeatherResponse.Condition(primary.id(), primary.main(), primary.description(), primary.icon());
    }

    private static Instant toInstant(Long epochSecond) {
        return epochSecond == null ? null : Instant.ofEpochSecond(epochSecond);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
