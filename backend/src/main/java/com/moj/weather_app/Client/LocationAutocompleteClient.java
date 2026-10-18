package com.moj.weather_app.Client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * City prefix search via the Open-Meteo Geocoding API (free, no API key, GeoNames data, CC BY 4.0).
 * OpenWeather's geocoding only matches whole names ("Lond" finds a village called Lond, not London),
 * so it cannot power autocomplete.
 */
@Component
public class LocationAutocompleteClient {
    private static final String PROVIDER = "Open-Meteo";
    private static final String SEARCH_PATH = "/v1/search";

    private final RestClient restClient;

    public LocationAutocompleteClient(@Qualifier("autocompleteRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** Returns up to {@code count} places whose name starts with the prefix, most relevant first. */
    public List<Place> search(String prefix, int count) {
        SearchResponse response = ExternalApiCall.execute(PROVIDER, SEARCH_PATH, () -> restClient.get()
                .uri(uri -> uri.path(SEARCH_PATH)
                        .queryParam("name", prefix)
                        .queryParam("count", count)
                        .queryParam("language", "en")
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .body(SearchResponse.class));
        // "results" is omitted entirely when nothing matches.
        return response.results() == null ? List.of() : response.results();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResponse(List<Place> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(String name, @JsonProperty("country_code") String countryCode) {
    }
}
