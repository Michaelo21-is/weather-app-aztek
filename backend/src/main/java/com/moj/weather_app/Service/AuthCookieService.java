package com.moj.weather_app.Service;

import com.moj.weather_app.Configuration.CookieProperties;
import com.moj.weather_app.Dto.AuthTokens;
import com.moj.weather_app.Enum.TokenType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

/** Stores the JWTs in HttpOnly cookies so JavaScript (and therefore XSS) cannot read them. */
@Service
public class AuthCookieService {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String ACCESS_TOKEN_PATH = "/";
    // The refresh token is only sent to the auth endpoints, never with regular API calls.
    private static final String REFRESH_TOKEN_PATH = "/api/auth";

    private final CookieProperties cookieProperties;
    private final JwtService jwtService;

    public AuthCookieService(CookieProperties cookieProperties, JwtService jwtService) {
        this.cookieProperties = cookieProperties;
        this.jwtService = jwtService;
    }

    public void addTokenCookies(HttpServletResponse response, AuthTokens tokens) {
        addCookie(response, ACCESS_TOKEN_COOKIE, tokens.accessToken(), ACCESS_TOKEN_PATH,
                jwtService.getExpiration(TokenType.ACCESS));
        addCookie(response, REFRESH_TOKEN_COOKIE, tokens.refreshToken(), REFRESH_TOKEN_PATH,
                jwtService.getExpiration(TokenType.REFRESH));
    }

    public void clearTokenCookies(HttpServletResponse response) {
        addCookie(response, ACCESS_TOKEN_COOKIE, "", ACCESS_TOKEN_PATH, Duration.ZERO);
        addCookie(response, REFRESH_TOKEN_COOKIE, "", REFRESH_TOKEN_PATH, Duration.ZERO);
    }

    public String readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_TOKEN_COOKIE);
    }

    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_TOKEN_COOKIE);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie cookie = WebUtils.getCookie(request, name);
        return cookie == null || cookie.getValue() == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    }

    private void addCookie(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
