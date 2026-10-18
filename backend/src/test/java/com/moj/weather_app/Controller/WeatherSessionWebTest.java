package com.moj.weather_app.Controller;

import com.moj.weather_app.Configuration.CookieProperties;
import com.moj.weather_app.Configuration.CorsProperties;
import com.moj.weather_app.Configuration.JwtProperties;
import com.moj.weather_app.Configuration.SecurityConfig;
import com.moj.weather_app.Configuration.WeatherSessionProperties;
import com.moj.weather_app.Dto.AuthTokens;
import com.moj.weather_app.Enum.TokenType;
import com.moj.weather_app.Service.AuthCookieService;
import com.moj.weather_app.Service.AuthService;
import com.moj.weather_app.Service.JwtService;
import com.moj.weather_app.Service.WeatherService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Weather session cookie behaviour through the real security filter chain and auth endpoints. */
@WebMvcTest(controllers = {WeatherController.class, AuthController.class},
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-123"
        })
@Import({SecurityConfig.class, AuthCookieService.class, JwtService.class})
// Slice tests skip @ConfigurationPropertiesScan.
@EnableConfigurationProperties({JwtProperties.class, CookieProperties.class, CorsProperties.class,
        WeatherSessionProperties.class})
class WeatherSessionWebTest {
    private static final String SESSION_ID = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE";
    private static final Cookie SESSION_COOKIE = new Cookie("weather_session", SESSION_ID);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private WeatherService weatherService;
    @MockitoBean
    private AuthService authService;

    @Test
    void anonymousVisitorGetsSessionCookieOnFirstRequest() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/weather/locations").param("query", "Lon"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(sessionCookie(response))
                .matches("weather_session=[A-Za-z0-9_-]{43};.*")
                .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/", "Max-Age=2592000");
        verify(weatherService).searchLocations("Lon");
    }

    @Test
    void existingSessionIsReusedAcrossRequests() throws Exception {
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = mockMvc.perform(get("/api/weather/coordinates")
                            .param("lat", "51.5").param("lon", "-0.12").cookie(SESSION_COOKIE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse();
            assertThat(sessionCookie(response)).isNull();
        }
        verify(weatherService, times(2))
                .getWeatherByCoordinates(SESSION_ID, 51.5, -0.12);
    }

    @Test
    void invalidSessionCookieIsReplaced() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/weather/coordinates")
                        .param("lat", "51.5").param("lon", "-0.12").param("refresh", "true")
                        .cookie(new Cookie("weather_session", "forged<script>")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String newId = sessionCookie(response).substring("weather_session=".length(), "weather_session=".length() + 43);
        verify(weatherService).getWeatherByCoordinates(newId, 51.5, -0.12);
    }

    @Test
    void authenticatedUserGetsSessionIdIndependentOfUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        Cookie accessToken = new Cookie("access_token", jwtService.generateToken(userId, TokenType.ACCESS));

        MockHttpServletResponse response = mockMvc.perform(get("/api/weather/coordinates")
                        .param("lat", "51.5").param("lon", "-0.12").cookie(accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(sessionCookie(response)).isNotNull().doesNotContain(userId.toString());
    }

    @Test
    void signInDoesNotChangeWeatherSession() throws Exception {
        when(authService.signIn(any())).thenReturn(new AuthTokens("access.jwt", "refresh.jwt"));

        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/sign-in").with(csrf())
                        .cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("access_token=access.jwt"))
                .noneMatch(cookie -> cookie.startsWith("weather_session="));

        // The same weather session keeps working once signed in.
        Cookie accessToken = new Cookie("access_token", jwtService.generateToken(UUID.randomUUID(), TokenType.ACCESS));
        mockMvc.perform(get("/api/weather/coordinates").param("lat", "1").param("lon", "2")
                        .cookie(SESSION_COOKIE, accessToken))
                .andExpect(status().isOk());
        verify(weatherService).getWeatherByCoordinates(SESSION_ID, 1, 2);
    }

    @Test
    void signOutDoesNotClearWeatherSession() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/sign-out").with(csrf())
                        .cookie(SESSION_COOKIE, new Cookie("refresh_token", "refresh.jwt")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies)
                .anyMatch(cookie -> cookie.startsWith("access_token=;") && cookie.contains("Max-Age=0"))
                .noneMatch(cookie -> cookie.startsWith("weather_session="));

        mockMvc.perform(get("/api/weather/coordinates").param("lat", "1").param("lon", "2").cookie(SESSION_COOKIE))
                .andExpect(status().isOk());
        verify(weatherService).getWeatherByCoordinates(SESSION_ID, 1, 2);
    }

    @Test
    void invalidParametersAreRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(get("/api/weather/coordinates").param("lat", "91").param("lon", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/weather/city").param("name", "London").param("country", "GBR"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/weather/locations").param("query", "L"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/weather/locations").param("query", "Lon{x}"))
                .andExpect(status().isBadRequest());

        verify(weatherService, never()).getWeatherByCoordinates(anyString(), anyDouble(), anyDouble());
        verify(weatherService, never()).getWeatherByCity(anyString(), eq("GBR"));
        verify(weatherService, never()).searchLocations(anyString());
    }

    private static String sessionCookie(MockHttpServletResponse response) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("weather_session="))
                .findFirst()
                .orElse(null);
    }
}
