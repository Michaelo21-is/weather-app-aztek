package com.moj.weather_app.Configuration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SessionIdFilterTest {
    private static final String VALID_ID = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE";

    private final SessionIdFilter filter = new SessionIdFilter(
            new WeatherSessionProperties("weather_session", Duration.ofDays(30)),
            new CookieProperties(true, "Lax"));

    @Test
    void visitorWithoutCookieGetsSecureSessionCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/weather/coordinates");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String sessionId = (String) request.getAttribute(SessionIdFilter.SESSION_ID_ATTRIBUTE);
        assertThat(sessionId).matches("[A-Za-z0-9_-]{43}");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("weather_session=" + sessionId + ";")
                .contains("Path=/", "Max-Age=2592000", "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void validCookieIsReusedWithoutReissuing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/weather/coordinates");
        request.setCookies(new Cookie("weather_session", VALID_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(SessionIdFilter.SESSION_ID_ATTRIBUTE)).isEqualTo(VALID_ID);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void malformedCookieIsReplaced() throws Exception {
        for (String invalid : new String[]{"", "short", VALID_ID + "x", VALID_ID.substring(1) + "*", "../../etc"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/weather/coordinates");
            request.setCookies(new Cookie("weather_session", invalid));
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            String sessionId = (String) request.getAttribute(SessionIdFilter.SESSION_ID_ATTRIBUTE);
            assertThat(sessionId).isNotEqualTo(invalid).matches("[A-Za-z0-9_-]{43}");
            assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith("weather_session=" + sessionId + ";");
        }
    }

    @Test
    void generatedIdsAreUnique() throws Exception {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            ids.add((String) request.getAttribute(SessionIdFilter.SESSION_ID_ATTRIBUTE));
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    void corsPreflightIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/weather/coordinates");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }
}
