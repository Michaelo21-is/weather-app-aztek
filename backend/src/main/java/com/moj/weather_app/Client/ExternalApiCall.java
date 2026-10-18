package com.moj.weather_app.Client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

/**
 * Translates failures of outgoing HTTP calls into gateway errors for our clients.
 * Exception messages are never logged: Spring includes the full request URL in them, which for OpenWeather
 * contains the API key.
 */
@Slf4j
final class ExternalApiCall {
    private ExternalApiCall() {
    }

    static <T> T execute(String provider, String path, Supplier<T> request) {
        T body;
        try {
            body = request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("{} {} responded with HTTP {}", provider, path, status);
            // 429 means our shared quota is exhausted: temporary, so 503 rather than 502.
            HttpStatus mapped = status == HttpStatus.TOO_MANY_REQUESTS.value()
                    ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(mapped, "Weather provider error");
        } catch (ResourceAccessException e) {
            // Connect/read timeouts and other I/O errors.
            log.warn("{} {} is unreachable: {}", provider, path,
                    e.getCause() == null ? "unknown cause" : e.getCause().getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Weather provider is unavailable");
        } catch (RestClientException e) {
            log.warn("{} {} returned an unreadable response", provider, path);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Weather provider error");
        }
        if (body == null) {
            log.warn("{} {} returned an empty body", provider, path);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Weather provider error");
        }
        return body;
    }
}
