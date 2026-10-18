package com.moj.weather_app.Service;

import com.moj.weather_app.Client.LocationAutocompleteClient;
import com.moj.weather_app.Client.OpenWeatherClient;
import com.moj.weather_app.Client.OpenWeatherClient.CurrentWeather;
import com.moj.weather_app.Client.OpenWeatherClient.Forecast;
import com.moj.weather_app.Client.OpenWeatherClient.GeocodingResult;
import com.moj.weather_app.Configuration.WeatherProperties;
import com.moj.weather_app.Dto.LocationSuggestionDto;
import com.moj.weather_app.Dto.FavoriteLocationDto;
import com.moj.weather_app.Response.FavoriteLocationWeatherResponse;
import com.moj.weather_app.Entity.FavoriteLocation;
import com.moj.weather_app.Entity.Users;
import com.moj.weather_app.Repository.FavoriteLocationRepository;
import com.moj.weather_app.Repository.UsersRepository;
import com.moj.weather_app.Response.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.util.*;


@Service
@Slf4j
public class WeatherService {
    static final String SESSION_KEY_PREFIX = "weather:session:";
    static final String CITY_KEY_PREFIX = "weather:city:";
    static final String GEOCODING_KEY_PREFIX = "weather:geocode:";
    static final String LOCATIONS_KEY_PREFIX = "weather:locations:";
    static final int MAX_SUGGESTIONS = 5;
    // A position shared with city searches is rounded to 0.1° (about 11 km) so it does not reveal where the visitor is.
    private static final double SHARED_COORDINATE_PRECISION = 10;
    // Extra candidates so duplicates (same name and country) can be dropped while still filling every slot.
    private static final int AUTOCOMPLETE_CANDIDATES = 10;

    private final OpenWeatherClient openWeatherClient;
    private final LocationAutocompleteClient autocompleteClient;
    private final RedisService redisService;
    private final JsonMapper jsonMapper;
    private final WeatherProperties properties;
    private final Clock clock;
    private final UsersRepository usersRepository;
    private final FavoriteLocationRepository favoriteLocationRepository;
    @Value("${favorite.location.max}")
    private int maxFavoriteLocations;

    public WeatherService(OpenWeatherClient openWeatherClient, LocationAutocompleteClient autocompleteClient,
                          RedisService redisService, JsonMapper jsonMapper, WeatherProperties properties, Clock clock,
                          UsersRepository usersRepository, FavoriteLocationRepository favoriteLocationRepository) {
        this.openWeatherClient = openWeatherClient;
        this.autocompleteClient = autocompleteClient;
        this.redisService = redisService;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.clock = clock;
        this.usersRepository = usersRepository;
        this.favoriteLocationRepository = favoriteLocationRepository;
    }

    /**
     * Returns the visitor's cached weather if present; the coordinates are only used on a cache miss.
     * {@code refresh} bypasses the cache so the visitor can update their current location.
     */
    public WeatherResponse getWeatherByCoordinates(String sessionId, double lat, double lon) {
        String key = SESSION_KEY_PREFIX + sessionId;

        WeatherResponse cached = readCache(key, WeatherResponse.class);
        if (cached != null) {
            return cached;
        }

        CurrentWeather current = openWeatherClient.currentWeather(lat, lon);
        Forecast forecast = openWeatherClient.forecast(lat, lon);
        WeatherResponse weather = WeatherMapper.toResponse(current, forecast,
                WeatherMapper.coordinatesLocation(current, forecast, lat, lon), properties.units(), clock.instant());
        cacheUntilNextFullHour(key, weather);
        shareWithCitySearches(weather);
        return weather;
    }

    /**
     * Serves a city's weather from the cache shared by all visitors, keyed by city and country name. The name as
     * requested is tried first, so a city already cached (also by a coordinates search) needs no geocoding. Otherwise
     * the city is geocoded and cached under its geocoded name, so aliases (e.g. Munich and München) share one entry.
     */
    public WeatherResponse getWeatherByCity(String cityName, String countryCode) {
        String country = countryCode.toUpperCase(Locale.ROOT);
        String requestedKey = cityKey(cityName, country);
        WeatherResponse cached = readCache(requestedKey, WeatherResponse.class);
        if (cached != null) {
            return cached;
        }
        ResolvedCity city = resolveCity(cityName.trim(), country);
        String key = cityKey(city.name(), city.country());
        cached = key.equals(requestedKey) ? null : readCache(key, WeatherResponse.class);
        if (cached != null) {
            return cached;
        }
        CurrentWeather current = openWeatherClient.currentWeather(city.lat(), city.lon());
        Forecast forecast = openWeatherClient.forecast(city.lat(), city.lon());
        // The geocoded name is used rather than the weather response's, which can be a district or suburb.
        WeatherResponse.Location location = new WeatherResponse.Location(city.name(), city.state(), city.country(),
                city.lat(), city.lon(), WeatherMapper.utcOffsetSeconds(current, forecast));
        WeatherResponse weather = WeatherMapper.toResponse(current, forecast, location, properties.units(),
                clock.instant());
        cacheUntilNextFullHour(key, weather);
        return weather;
    }

    /** Up to {@value #MAX_SUGGESTIONS} cities starting with the prefix, as name and country code only. */
    public List<LocationSuggestionDto> searchLocations(String query) {
        String prefix = query.trim().replaceAll("\\s+", " ");
        String key = LOCATIONS_KEY_PREFIX + prefix.toLowerCase(Locale.ROOT);
        LocationSuggestionDto[] cached = readCache(key, LocationSuggestionDto[].class);
        if (cached != null) {
            return List.of(cached);
        }
        String normalizedPrefix = normalizeName(prefix);
        Map<String, LocationSuggestionDto> unique = new LinkedHashMap<>();
        for (LocationAutocompleteClient.Place place : autocompleteClient.search(prefix, AUTOCOMPLETE_CANDIDATES)) {
            // Open-Meteo also matches alternate names (e.g. "São P" finds Luanda); keep true prefix matches only.
            if (place.name() == null || place.countryCode() == null
                    || !normalizeName(place.name()).startsWith(normalizedPrefix)) {
                continue;
            }
            LocationSuggestionDto suggestion =
                    new LocationSuggestionDto(place.name(), place.countryCode().toUpperCase(Locale.ROOT));
            unique.putIfAbsent(suggestion.name().toLowerCase(Locale.ROOT) + "|" + suggestion.country(), suggestion);
        }
        List<LocationSuggestionDto> suggestions = unique.values().stream().limit(MAX_SUGGESTIONS).toList();
        writeCache(key, suggestions, properties.autocompleteTtl());
        return suggestions;
    }
    @Transactional
    public void saveFavoriteLocation(UUID userId, FavoriteLocationDto favoriteLocation) {
        Users user = usersRepository.findById(userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")
        );
        if (user.getFavoriteCityCounter() >= maxFavoriteLocations){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have reached the maximum number of favorite locations 10");
        }
        boolean isDuplicate = favoriteLocationRepository.existsByUsers_idAndCityNameAndCountryName(user.getId(), favoriteLocation.cityName(), favoriteLocation.countryName());
        if (isDuplicate){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "you already have this location saved as a favorite");
        }
        FavoriteLocation location = FavoriteLocation.builder()
                .users(user)
                .cityName(favoriteLocation.cityName())
                .countryName(favoriteLocation.countryName())
                .build();
        favoriteLocationRepository.save(location);
        user.setFavoriteCityCounter(user.getFavoriteCityCounter() + 1);
        usersRepository.save(user);
    }
    @Transactional
    public void removeFavoriteLocation(UUID userId, FavoriteLocationDto favoriteLocationDto) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        int deleted = favoriteLocationRepository.deleteByUsers_idAndCityNameAndCountryName(userId, favoriteLocationDto.cityName(), favoriteLocationDto.countryName());

        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorite location not exist in the system");
        }

        user.setFavoriteCityCounter(user.getFavoriteCityCounter() - deleted);
        usersRepository.save(user);
    }

    /** The user's favorite locations with their current weather, served through {@link #getWeatherByCity}. */
    public List<FavoriteLocationWeatherResponse> getUserFavoriteLocations(UUID userId) {
        return favoriteLocationRepository.findByUsers_id(userId).stream()
                .map(location -> new FavoriteLocationWeatherResponse(location.getCityName(), location.getCountryName(),
                        getWeatherByCity(location.getCityName(), location.getCountryName())))
                .toList();
    }

    /**
     * Picks one city for an ambiguous name: results are restricted to the requested country, results named exactly
     * like the query (in any language, ignoring case and accents) beat fuzzy matches, and remaining ties
     * (e.g. Springfield, US) go to the geocoder's most relevant result. The chosen state is returned in the response.
     * Geocoding results rarely change, so they are cached for {@code weather.geocoding-ttl}.
     */
    private ResolvedCity resolveCity(String cityName, String countryCode) {
        String key = GEOCODING_KEY_PREFIX + countryCode + ":" + cityName.toLowerCase(Locale.ROOT);
        ResolvedCity cached = readCache(key, ResolvedCity.class);
        if (cached != null) {
            return cached;
        }
        List<GeocodingResult> matches = openWeatherClient.geocode(cityName, countryCode).stream()
                .filter(result -> countryCode.equalsIgnoreCase(result.country()))
                .toList();
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found");
        }
        String wanted = normalizeName(cityName);
        GeocodingResult best = matches.stream()
                .filter(result -> namedExactly(result, wanted))
                .findFirst()
                .orElse(matches.get(0));
        ResolvedCity city = new ResolvedCity(best.name(), best.state(), best.country().toUpperCase(Locale.ROOT),
                best.lat(), best.lon());
        writeCache(key, city, properties.geocodingTtl());
        return city;
    }

    private static boolean namedExactly(GeocodingResult result, String normalizedName) {
        if (result.name() != null && normalizeName(result.name()).equals(normalizedName)) {
            return true;
        }
        return result.localNames() != null && result.localNames().values().stream()
                .filter(Objects::nonNull)
                .anyMatch(localName -> normalizeName(localName).equals(normalizedName));
    }

    private static String normalizeName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    /** The shared city cache key: one per city and country, ignoring case, accents and repeated spaces in the name. */
    static String cityKey(String cityName, String countryCode) {
        return CITY_KEY_PREFIX + countryCode.trim().toUpperCase(Locale.ROOT) + ":"
                + normalizeName(cityName).replaceAll("\\s+", " ");
    }

    /**
     * Also caches a position's weather for searches of the place OpenWeather names for it, unless that city is already
     * cached (a geocoded city search entry is kept). The entry is served to everyone, so the coordinates are coarsened.
     */
    private void shareWithCitySearches(WeatherResponse weather) {
        WeatherResponse.Location location = weather.location();
        if (location.name() == null || location.country() == null) {
            return;
        }
        String key = cityKey(location.name(), location.country());
        if (readCache(key, WeatherResponse.class) != null) {
            return;
        }
        WeatherResponse.Location coarse = new WeatherResponse.Location(location.name(), location.state(),
                location.country(), coarsen(location.lat()), coarsen(location.lon()), location.utcOffsetSeconds());
        cacheUntilNextFullHour(key, new WeatherResponse(coarse, weather.current(), weather.hourly(), weather.daily(),
                weather.units(), weather.fetchedAt()));
    }

    private static double coarsen(double coordinate) {
        return Math.round(coordinate * SHARED_COORDINATE_PRECISION) / SHARED_COORDINATE_PRECISION;
    }

    private void cacheUntilNextFullHour(String key, WeatherResponse weather) {
        Duration ttl = WeatherCacheTtl.untilNextFullHour(clock.instant(), weather.location().utcOffsetSeconds());
        writeCache(key, weather, ttl);
    }

    private <T> T readCache(String key, Class<T> type) {
        try {
            String json = redisService.readRedis(key);
            return json == null ? null : jsonMapper.readValue(json, type);
        } catch (DataAccessException | JacksonException e) {
            // Unreachable Redis or an entry written by an older version: treat as a miss.
            log.warn("Weather cache read failed, falling back to the provider: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void writeCache(String key, Object value, Duration ttl) {
        try {
            redisService.uploadRedis(key, jsonMapper.writeValueAsString(value), ttl);
        } catch (DataAccessException | JacksonException e) {
            log.warn("Weather cache write failed: {}", e.getClass().getSimpleName());
        }
    }

    record ResolvedCity(String name, String state, String country, double lat, double lon) {
    }
}
