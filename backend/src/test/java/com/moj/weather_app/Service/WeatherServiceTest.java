package com.moj.weather_app.Service;

import com.moj.weather_app.Client.LocationAutocompleteClient;
import com.moj.weather_app.Client.LocationAutocompleteClient.Place;
import com.moj.weather_app.Client.OpenWeatherClient;
import com.moj.weather_app.Client.OpenWeatherClient.City;
import com.moj.weather_app.Client.OpenWeatherClient.Condition;
import com.moj.weather_app.Client.OpenWeatherClient.CurrentWeather;
import com.moj.weather_app.Client.OpenWeatherClient.Forecast;
import com.moj.weather_app.Client.OpenWeatherClient.ForecastItem;
import com.moj.weather_app.Client.OpenWeatherClient.GeocodingResult;
import com.moj.weather_app.Client.OpenWeatherClient.Main;
import com.moj.weather_app.Client.OpenWeatherClient.Sys;
import com.moj.weather_app.Client.OpenWeatherClient.Wind;
import com.moj.weather_app.Configuration.WeatherProperties;
import com.moj.weather_app.Dto.LocationSuggestionDto;
import com.moj.weather_app.Repository.FavoriteLocationRepository;
import com.moj.weather_app.Repository.UsersRepository;
import com.moj.weather_app.Response.WeatherResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherServiceTest {
    // 10:15 UTC: weather cached now must expire in 45 minutes in a UTC+0 location.
    private static final Instant NOW = Instant.parse("2026-10-17T10:15:00Z");
    private static final String SESSION_ID = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE";
    private static final String OTHER_SESSION_ID = "ZyXwVuTsRqPoNmLkJiHgFeDcBa9876543210_-ZyXwV";

    private final OpenWeatherClient openWeatherClient = mock(OpenWeatherClient.class);
    private final LocationAutocompleteClient autocompleteClient = mock(LocationAutocompleteClient.class);
    private final UsersRepository usersRepository =
            mock(UsersRepository.class);

    private final FavoriteLocationRepository favoriteLocationRepository =
            mock(FavoriteLocationRepository.class);
    private final InMemoryRedisService redis = new InMemoryRedisService();
    private final WeatherProperties properties = new WeatherProperties("key", "https://ow.test", "https://om.test",
            "metric", Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofHours(24), Duration.ofDays(7));
    private final WeatherService service = new WeatherService(
            openWeatherClient,
            autocompleteClient,
            redis,
            JsonMapper.builder().build(),
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC),
            usersRepository,
            favoriteLocationRepository
    );

    // ---------- Personal (session) cache ----------

    @Test
    void sessionCacheMissFetchesWeatherAndCachesUntilNextHour() {
        stubWeather(51.5, -0.12, "London", "GB", 0);

        WeatherResponse weather = service.getWeatherByCoordinates(SESSION_ID, 51.5, -0.12);

        assertThat(weather.location().name()).isEqualTo("London");
        assertThat(weather.location().country()).isEqualTo("GB");
        assertThat(weather.current().temperature()).isEqualTo(14.0);
        assertThat(weather.hourly()).hasSize(3);
        assertThat(weather.daily()).extracting(WeatherResponse.Daily::date)
                .containsExactly(LocalDate.parse("2026-10-17"), LocalDate.parse("2026-10-18"));
        assertThat(redis.values).containsKey("weather:session:" + SESSION_ID);
        assertThat(redis.ttls.get("weather:session:" + SESSION_ID)).isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    void sessionCacheHitReturnsCachedWeatherWithoutCallingProvider() {
        stubWeather(51.5, -0.12, "London", "GB", 0);
        WeatherResponse first = service.getWeatherByCoordinates(SESSION_ID, 51.5, -0.12);

        // Different coordinates on a hit: the cached weather is still returned (refresh is explicit).
        WeatherResponse second = service.getWeatherByCoordinates(SESSION_ID, 48.85, 2.35);

        assertThat(second).isEqualTo(first);
        verify(openWeatherClient, times(1)).currentWeather(anyDouble(), anyDouble());
        verify(openWeatherClient, times(1)).forecast(anyDouble(), anyDouble());
    }

    @Test
    void refreshBypassesSessionCacheAndStoresNewLocation() {
        stubWeather(51.5, -0.12, "London", "GB", 0);
        stubWeather(48.85, 2.35, "Paris", "FR", 7200);
        service.getWeatherByCoordinates(SESSION_ID, 51.5, -0.12);

        WeatherResponse refreshed = service.getWeatherByCoordinates(SESSION_ID, 48.85, 2.35);
        WeatherResponse cached = service.getWeatherByCoordinates(SESSION_ID, 0, 0);

        assertThat(refreshed.location().name()).isEqualTo("Paris");
        assertThat(cached).isEqualTo(refreshed);
    }

    @Test
    void sessionsAreCachedIndependently() {
        stubWeather(51.5, -0.12, "London", "GB", 0);
        stubWeather(48.85, 2.35, "Paris", "FR", 7200);

        service.getWeatherByCoordinates(SESSION_ID, 51.5, -0.12);
        WeatherResponse other = service.getWeatherByCoordinates(OTHER_SESSION_ID, 48.85, 2.35);

        assertThat(other.location().name()).isEqualTo("Paris");
        assertThat(redis.values).containsKeys("weather:session:" + SESSION_ID, "weather:session:" + OTHER_SESSION_ID);
    }

    @Test
    void ttlUsesLocationTimezone() {
        // UTC+5:30: 10:15 UTC is 15:45 local, so the entry lives 15 minutes.
        stubWeather(28.61, 77.21, "New Delhi", "IN", 19800);

        WeatherResponse weather = service.getWeatherByCoordinates(SESSION_ID, 28.61, 77.21);

        assertThat(weather.location().utcOffsetSeconds()).isEqualTo(19800);
        assertThat(redis.ttls.get("weather:session:" + SESSION_ID)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void redisOutageFallsBackToProvider() {
        stubWeather(51.5, -0.12, "London", "GB", 0);
        redis.failing = true;

        WeatherResponse weather = service.getWeatherByCoordinates(SESSION_ID, 51.5, -0.12);

        assertThat(weather.location().name()).isEqualTo("London");
    }

    // ---------- Shared city cache ----------

    @Test
    void cityCacheMissGeocodesFetchesAndCaches() {
        when(openWeatherClient.geocode("London", "GB")).thenReturn(List.of(
                new GeocodingResult("London", Map.of("en", "London"), 51.5073219, -0.1276474, "GB", "England")));
        stubWeather(51.5073219, -0.1276474, "City of Westminster", "GB", 3600);

        WeatherResponse weather = service.getWeatherByCity("London", "gb");

        assertThat(weather.location().name()).isEqualTo("London");
        assertThat(weather.location().state()).isEqualTo("England");
        assertThat(weather.location().country()).isEqualTo("GB");
        assertThat(redis.values).containsKeys("weather:city:51.5073,-0.1276", "weather:geocode:GB:london");
        assertThat(redis.ttls.get("weather:city:51.5073,-0.1276")).isEqualTo(Duration.ofMinutes(45));
        assertThat(redis.values.keySet()).noneMatch(key -> key.startsWith("weather:session:"));
    }

    @Test
    void differentVisitorsShareCityCache() {
        when(openWeatherClient.geocode("London", "GB")).thenReturn(List.of(
                new GeocodingResult("London", null, 51.5073219, -0.1276474, "GB", "England")));
        stubWeather(51.5073219, -0.1276474, "London", "GB", 3600);

        WeatherResponse first = service.getWeatherByCity("London", "GB");
        WeatherResponse second = service.getWeatherByCity("london", "gb");

        assertThat(second).isEqualTo(first);
        verify(openWeatherClient, times(1)).geocode(anyString(), anyString());
        verify(openWeatherClient, times(1)).currentWeather(anyDouble(), anyDouble());
        verify(openWeatherClient, times(1)).forecast(anyDouble(), anyDouble());
    }

    @Test
    void ambiguousCityPrefersExactNameInRequestedCountry() {
        when(openWeatherClient.geocode("Springfield", "US")).thenReturn(List.of(
                new GeocodingResult("Springfield Gardens", null, 40.66, -73.76, "US", "New York"),
                new GeocodingResult("Springfield", null, 52.0, 0.5, "GB", "England"),
                new GeocodingResult("Springfield", null, 39.799, -89.644, "US", "Illinois"),
                new GeocodingResult("Springfield", null, 42.102, -72.589, "US", "Massachusetts")));
        stubWeather(39.799, -89.644, "Springfield", "US", -18000);

        WeatherResponse weather = service.getWeatherByCity("Springfield", "US");

        assertThat(weather.location().state()).isEqualTo("Illinois");
        assertThat(weather.location().lat()).isEqualTo(39.799);
    }

    @Test
    void exactNameMatchIgnoresAccentsAndUsesLocalNames() {
        when(openWeatherClient.geocode("Munich", "DE")).thenReturn(List.of(
                new GeocodingResult("Munchen-Ost", null, 48.13, 11.6, "DE", "Bavaria"),
                new GeocodingResult("München", Map.of("en", "Munich"), 48.137, 11.575, "DE", "Bavaria")));
        stubWeather(48.137, 11.575, "Munich", "DE", 7200);

        assertThat(service.getWeatherByCity("Munich", "DE").location().name()).isEqualTo("München");
    }

    @Test
    void unknownCityIsNotFound() {
        when(openWeatherClient.geocode("Atlantis", "GR")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getWeatherByCity("Atlantis", "GR"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(openWeatherClient, never()).currentWeather(anyDouble(), anyDouble());
    }

    // ---------- Autocomplete cache ----------

    @Test
    void autocompleteReturnsFiveUniquePrefixMatchesAndCachesThem() {
        when(autocompleteClient.search("Lon", 10)).thenReturn(List.of(
                new Place("London", "GB"), new Place("London", "CA"), new Place("London", "gb"),
                new Place("Londrina", "BR"), new Place(null, "US"), new Place("Luanda", "AO"),
                new Place("Long Beach", "US"),
                new Place("Longueuil", "CA"), new Place("Lonavala", "IN")));

        List<LocationSuggestionDto> first = service.searchLocations("  Lon ");
        List<LocationSuggestionDto> second = service.searchLocations("lon");

        assertThat(first).containsExactly(
                new LocationSuggestionDto("London", "GB"), new LocationSuggestionDto("London", "CA"),
                new LocationSuggestionDto("Londrina", "BR"), new LocationSuggestionDto("Long Beach", "US"),
                new LocationSuggestionDto("Longueuil", "CA"));
        assertThat(second).isEqualTo(first);
        verify(autocompleteClient, times(1)).search(anyString(), anyInt());
        assertThat(redis.ttls.get("weather:locations:lon")).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void autocompletePrefixMatchIgnoresAccents() {
        when(autocompleteClient.search("Sao P", 10)).thenReturn(List.of(
                new Place("São Paulo", "BR"), new Place("Luanda", "AO"), new Place("São Pedro", "BR")));

        assertThat(service.searchLocations("Sao P")).containsExactly(
                new LocationSuggestionDto("São Paulo", "BR"), new LocationSuggestionDto("São Pedro", "BR"));
    }

    // ---------- Fixtures ----------

    private void stubWeather(double lat, double lon, String name, String country, int offsetSeconds) {
        Condition clouds = new Condition(803, "Clouds", "broken clouds", "04d");
        Condition rain = new Condition(500, "Rain", "light rain", "10d");
        when(openWeatherClient.currentWeather(lat, lon)).thenReturn(new CurrentWeather(name, NOW.getEpochSecond(),
                offsetSeconds, new Main(14.0, 13.2, 13.0, 15.0, 70), List.of(clouds), new Wind(4.1),
                new Sys(country, 1792218000L, 1792258000L)));
        when(openWeatherClient.forecast(lat, lon)).thenReturn(new Forecast(List.of(
                new ForecastItem(Instant.parse("2026-10-17T12:00:00Z").getEpochSecond(),
                        new Main(15.0, 14.0, 14.5, 15.5, 65), List.of(clouds), 0.1),
                new ForecastItem(Instant.parse("2026-10-17T21:00:00Z").getEpochSecond(),
                        new Main(11.0, 10.0, 10.5, 11.2, 80), List.of(rain), 0.6),
                new ForecastItem(Instant.parse("2026-10-18T12:00:00Z").getEpochSecond(),
                        new Main(16.0, 15.0, 15.8, 16.3, 60), List.of(clouds), null)),
                new City(name, country, offsetSeconds, null, null)));
    }

    /** Records values and TTLs like Redis would; {@code failing} simulates an outage. */
    private static class InMemoryRedisService extends RedisService {
        final Map<String, String> values = new HashMap<>();
        final Map<String, Duration> ttls = new HashMap<>();
        boolean failing;

        InMemoryRedisService() {
            super(null);
        }

        @Override
        public String readRedis(String key) {
            failIfDown();
            return values.get(key);
        }

        @Override
        public void uploadRedis(String key, String value, Duration ttl) {
            failIfDown();
            values.put(key, value);
            ttls.put(key, ttl);
        }

        private void failIfDown() {
            if (failing) {
                throw new RedisConnectionFailureException("Redis is down");
            }
        }
    }
}
