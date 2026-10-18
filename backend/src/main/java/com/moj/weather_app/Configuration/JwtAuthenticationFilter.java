package com.moj.weather_app.Configuration;

import com.moj.weather_app.Enum.TokenType;
import com.moj.weather_app.Service.AuthCookieService;
import com.moj.weather_app.Service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates requests from the access token cookie. The principal is the user's ID ({@link UUID}),
 * available in controllers via {@code @AuthenticationPrincipal UUID userId}.
 * Not a @Component on purpose: Spring Boot would otherwise also register it as a plain servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AuthCookieService authCookieService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthCookieService authCookieService) {
        this.jwtService = jwtService;
        this.authCookieService = authCookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = authCookieService.readAccessToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Only ACCESS tokens are accepted here; a refresh token fails the type check.
                UUID userId = jwtService.extractUserId(token, TokenType.ACCESS);
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (JwtException e) {
                // Invalid or expired tokens leave the request unauthenticated, so public endpoints
                // (e.g. refresh) still work and protected ones answer 401.
            }
        }
        filterChain.doFilter(request, response);
    }
}
