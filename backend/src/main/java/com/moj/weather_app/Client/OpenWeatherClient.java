package com.moj.weather_app.Client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moj.weather_app.Configuration.WeatherProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * OpenWeather endpoints available on the free plan: Current Weather, 5 day / 3 hour Forecast and Geocoding.
 * (Hourly 4-day, daily 16-day and One Call 3.0 require a paid subscription and answer 401 for free keys.)
 */
@Component
public class OpenWeatherClient {
    private static final String PROVIDER = "OpenWeather";
    private static final String CURRENT_WEATHER_PATH = "/data/2.5/weather";
    private static final String FORECAST_PATH = "/data/2.5/forecast";
    private static final String GEOCODING_PATH = "/geo/1.0/direct";
    // Maximum allowed by the geocoding API.
    private static final int GEOCODING_LIMIT = 5;

    private final RestClient restClient;
    private final WeatherProperties properties;

    public OpenWeatherClient(@Qualifier("openWeatherRestClient") RestClient restClient, WeatherProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public CurrentWeather currentWeather(double lat, double lon) {
        return get(CURRENT_WEATHER_PATH, uri -> withCoordinates(uri, lat, lon), CurrentWeather.class);
    }

    /** 40 timestamps in 3-hour steps covering the next 5 days. */
    public Forecast forecast(double lat, double lon) {
        return get(FORECAST_PATH, uri -> withCoordinates(uri, lat, lon), Forecast.class);
    }

    /** Resolves a city name within a country (ISO 3166 alpha-2). Results are ordered by relevance. */
    public List<GeocodingResult> geocode(String cityName, String countryCode) {
        return List.of(get(GEOCODING_PATH,
                uri -> uri.queryParam("q", cityName + "," + countryCode).queryParam("limit", GEOCODING_LIMIT),
                GeocodingResult[].class));
    }

    private UriBuilder withCoordinates(UriBuilder uri, double lat, double lon) {
        return uri.queryParam("lat", lat).queryParam("lon", lon).queryParam("units", properties.units());
    }

    private <T> T get(String path, Function<UriBuilder, UriBuilder> params, Class<T> type) {
        return ExternalApiCall.execute(PROVIDER, path, () -> restClient.get()
                .uri(uri -> params.apply(uri.path(path)).queryParam("appid", properties.apiKey()).build())
                .retrieve()
                .body(type));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentWeather(String name, long dt, Integer timezone, Main main, List<Condition> weather,
                                 Wind wind, Sys sys) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Forecast(List<ForecastItem> list, City city) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForecastItem(long dt, Main main, List<Condition> weather, Double pop) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(double temp,
                       @JsonProperty("feels_like") double feelsLike,
                       @JsonProperty("temp_min") double tempMin,
                       @JsonProperty("temp_max") double tempMax,
                       int humidity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(int id, String main, String description, String icon) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(double speed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sys(String country, Long sunrise, Long sunset) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record City(String name, String country, Integer timezone, Long sunrise, Long sunset) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeocodingResult(String name, @JsonProperty("local_names") Map<String, String> localNames,
                                  double lat, double lon, String country, String state) {
    }
}
