package com.moj.weather_app.Configuration;

import com.moj.weather_app.Service.AuthCookieService;
import com.moj.weather_app.Service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/sign-up",
            "/api/auth/sign-in",
            "/api/auth/refresh",
            "/api/auth/sign-out",
            "/api/csrf",
            // Error dispatches must stay public, otherwise e.g. a 409 from sign-up turns into a 401.
            "/error",
            // Platform health probes. Only /api/** is proxied by the frontend, so this is not reachable publicly.
            "/actuator/health/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   AuthCookieService authCookieService,
                                                   CookieProperties cookieProperties) throws Exception {
        // HttpOnly cookie: the frontend gets the (BREACH-masked) token from GET /api/auth/csrf
        // and sends it back in the X-XSRF-TOKEN header.
        CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite()));

        http
                .cors(Customizer.withDefaults())
                // The JWTs live in cookies that the browser attaches automatically, so CSRF protection
                // stays enabled for every state-changing request, including sign-in and sign-up.
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Must precede the public weather GET rule below, which would otherwise match it.
                        .requestMatchers(HttpMethod.GET, "/api/weather/get-user-favorite-location").authenticated()
                        // Weather is available to every visitor; it is cached per weather session, not per user.
                        .requestMatchers(HttpMethod.GET, "/api/weather/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, authCookieService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
