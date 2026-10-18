package com.moj.weather_app.Configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Gives every visitor (signed in or not) a random weather session ID in an HttpOnly cookie and exposes it as the
 * {@link #SESSION_ID_ATTRIBUTE} request attribute. The ID only keys the visitor's personal weather cache: it is
 * independent of the user ID, JWTs and CSRF token, is never used for authentication, and is not touched by
 * sign-in or sign-out.
 * <p>
 * Runs as a plain servlet filter ahead of Spring Security, so it never interferes with authentication.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class SessionIdFilter extends OncePerRequestFilter {
    public static final String SESSION_ID_ATTRIBUTE = "weatherSessionId";

    private static final int SESSION_ID_BYTES = 32;
    // 32 random bytes in unpadded Base64URL are always exactly 43 characters.
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom = new SecureRandom();
    private final WeatherSessionProperties sessionProperties;
    private final CookieProperties cookieProperties;

    public SessionIdFilter(WeatherSessionProperties sessionProperties, CookieProperties cookieProperties) {
        this.sessionProperties = sessionProperties;
        this.cookieProperties = cookieProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // CORS preflights carry no cookies, so issuing an ID there would only create orphaned sessions.
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String sessionId = readValidSessionId(request);
        if (sessionId == null) {
            // Missing, tampered or malformed cookies are replaced instead of rejected.
            sessionId = generateSessionId();
            addSessionCookie(response, sessionId);
        }
        request.setAttribute(SESSION_ID_ATTRIBUTE, sessionId);
        filterChain.doFilter(request, response);
    }

    private String readValidSessionId(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, sessionProperties.cookieName());
        if (cookie == null || cookie.getValue() == null) {
            return null;
        }
        return SESSION_ID_PATTERN.matcher(cookie.getValue()).matches() ? cookie.getValue() : null;
    }

    private String generateSessionId() {
        byte[] bytes = new byte[SESSION_ID_BYTES];
        secureRandom.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private void addSessionCookie(HttpServletResponse response, String sessionId) {
        ResponseCookie cookie = ResponseCookie.from(sessionProperties.cookieName(), sessionId)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path("/")
                .maxAge(sessionProperties.maxAge())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
