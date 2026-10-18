package com.moj.weather_app.Client;

import com.moj.weather_app.Configuration.WeatherProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenWeatherClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://ow.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenWeatherClient client = new OpenWeatherClient(builder.build(), new WeatherProperties(
            "secret-key", "https://ow.test", "https://om.test", "metric",
            Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofHours(24), Duration.ofDays(7)));

    @Test
    void parsesCurrentWeatherAndSendsKeyServerSide() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://ow.test/data/2.5/weather?")))
                .andExpect(queryParam("lat", "32.08"))
                .andExpect(queryParam("lon", "34.78"))
                .andExpect(queryParam("units", "metric"))
                .andExpect(queryParam("appid", "secret-key"))
                .andRespond(withSuccess("""
                        {"coord":{"lon":34.78,"lat":32.08},
                         "weather":[{"id":500,"main":"Rain","description":"light rain","icon":"10d"}],
                         "main":{"temp":24.33,"feels_like":24.44,"temp_min":24.1,"temp_max":25.06,"humidity":62},
                         "wind":{"speed":2.06,"deg":170},"dt":1792219561,
                         "sys":{"country":"IL","sunrise":1792200000,"sunset":1792241000},
                         "timezone":10800,"id":293397,"name":"Tel Aviv","cod":200}
                        """, MediaType.APPLICATION_JSON));

        OpenWeatherClient.CurrentWeather weather = client.currentWeather(32.08, 34.78);

        assertThat(weather.name()).isEqualTo("Tel Aviv");
        assertThat(weather.timezone()).isEqualTo(10800);
        assertThat(weather.main().feelsLike()).isEqualTo(24.44);
        assertThat(weather.weather().get(0).description()).isEqualTo("light rain");
        assertThat(weather.sys().country()).isEqualTo("IL");
        server.verify();
    }

    @Test
    void providerErrorsBecomeGatewayErrors() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/data/2.5/forecast")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/data/2.5/forecast")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/data/2.5/forecast")))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertStatus(HttpStatus.BAD_GATEWAY);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertStatus(HttpStatus.GATEWAY_TIMEOUT);
    }

    private void assertStatus(HttpStatus expected) {
        assertThatThrownBy(() -> client.forecast(1, 2))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(expected);
                    assertThat(e.getMessage()).doesNotContain("secret-key");
                });
    }
}
