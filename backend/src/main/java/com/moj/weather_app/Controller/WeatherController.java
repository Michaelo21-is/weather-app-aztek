package com.moj.weather_app.Controller;

import com.moj.weather_app.Configuration.SessionIdFilter;
import com.moj.weather_app.Dto.LocationSuggestionDto;
import com.moj.weather_app.Dto.FavoriteLocationDto;
import com.moj.weather_app.Response.FavoriteLocationWeatherResponse;
import com.moj.weather_app.Response.WeatherResponse;
import com.moj.weather_app.Service.WeatherService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Public weather endpoints; invalid parameters are rejected with 400 before any cache or provider access. */
@RestController
@RequestMapping("/api/weather")
public class WeatherController {
    // Letters in any script plus the separators found in place names (e.g. "St. John's", "Aix-en-Provence").
    private static final String PLACE_NAME_PATTERN = "[\\p{L}\\p{M} .'’-]+";

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /** Weather for the visitor's location, cached per weather session and shared with searches for its city. */
    @GetMapping("/coordinates")
    public WeatherResponse byCoordinates(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @RequestAttribute(SessionIdFilter.SESSION_ID_ATTRIBUTE) String sessionId) {
        return weatherService.getWeatherByCoordinates(sessionId, lat, lon);
    }

    /** Weather for a city, cached for all visitors. {@code country} is an ISO 3166 alpha-2 code. */
    @GetMapping("/city")
    public WeatherResponse byCity(
            @RequestParam @NotBlank @Size(max = 100) @Pattern(regexp = PLACE_NAME_PATTERN) String cityName,
            @RequestParam @Pattern(regexp = "[A-Za-z]{2}") String countryName) {
        return weatherService.getWeatherByCity(cityName, countryName);
    }

    /** City autocomplete: up to 5 matches with name and country only. */
    @GetMapping("/city-suggestion")
    public List<LocationSuggestionDto> locations(
            @RequestParam @NotBlank @Size(min = 2, max = 60) @Pattern(regexp = PLACE_NAME_PATTERN) String query) {
        return weatherService.searchLocations(query);
    }
    @PostMapping("/save-favorite-location")
    public void saveFavoriteLocation(@AuthenticationPrincipal UUID userId, @RequestBody FavoriteLocationDto favoriteLocationDto){
        weatherService.saveFavoriteLocation(userId, favoriteLocationDto);
    }
    @DeleteMapping("/remove-favorite-location")
    public void removeFavoriteLocation(@AuthenticationPrincipal UUID userId, @RequestBody FavoriteLocationDto favoriteLocationDto){
        weatherService.removeFavoriteLocation(userId, favoriteLocationDto);
    }
    @GetMapping("/get-user-favorite-location")
    public List<FavoriteLocationWeatherResponse> getUserFavoriteLocations(@AuthenticationPrincipal UUID userId){
        return weatherService.getUserFavoriteLocations(userId);
    }
}
