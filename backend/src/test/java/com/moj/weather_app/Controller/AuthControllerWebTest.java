package com.moj.weather_app.Controller;

import com.moj.weather_app.Configuration.CookieProperties;
import com.moj.weather_app.Configuration.CorsProperties;
import com.moj.weather_app.Configuration.JwtProperties;
import com.moj.weather_app.Configuration.SecurityConfig;
import com.moj.weather_app.Configuration.WeatherSessionProperties;
import com.moj.weather_app.Dto.AuthTokens;
import com.moj.weather_app.Dto.SignUpDto;
import com.moj.weather_app.Service.AuthCookieService;
import com.moj.weather_app.Service.AuthService;
import com.moj.weather_app.Service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-up and sign-in set the same auth cookies, through the real security filter chain. */
@WebMvcTest(controllers = AuthController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-123"
        })
@Import({SecurityConfig.class, AuthCookieService.class, JwtService.class})
// Slice tests skip @ConfigurationPropertiesScan.
@EnableConfigurationProperties({JwtProperties.class, CookieProperties.class, CorsProperties.class,
        WeatherSessionProperties.class})
class AuthControllerWebTest {
    private static final String SIGN_UP_BODY = "{\"email\":\"new@example.com\",\"nickname\":\"Newbie\",\"password\":\"secret123\"}";
    private static final String SIGN_IN_BODY = "{\"email\":\"new@example.com\",\"password\":\"secret123\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AuthService authService;

    @Test
    void signUpSetsTheSameCookiesAsSignIn() throws Exception {
        AuthTokens tokens = new AuthTokens("access.jwt", "refresh.jwt");
        when(authService.signUp(any())).thenReturn(tokens);
        when(authService.signIn(any())).thenReturn(tokens);

        List<String> signUpCookies = authCookies("/api/auth/sign-up", SIGN_UP_BODY);
        List<String> signInCookies = authCookies("/api/auth/sign-in", SIGN_IN_BODY);

        assertThat(signUpCookies).containsExactlyElementsOf(signInCookies);
        assertThat(signUpCookies).hasSize(2);
        assertThat(signUpCookies.get(0)).startsWith("access_token=access.jwt;")
                .contains("Path=/;", "Max-Age=900", "HttpOnly", "Secure", "SameSite=Lax");
        assertThat(signUpCookies.get(1)).startsWith("refresh_token=refresh.jwt;")
                .contains("Path=/api/auth", "Max-Age=1209600", "HttpOnly", "Secure", "SameSite=Lax");
        verify(authService).signUp(new SignUpDto("new@example.com", "Newbie", "secret123"));
    }

    @Test
    void duplicateEmailSetsNoCookies() throws Exception {
        when(authService.signUp(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists"));

        List<String> cookies = mockMvc.perform(post("/api/auth/sign-up").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SIGN_UP_BODY))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);

        assertThat(cookies).noneMatch(cookie -> cookie.startsWith("access_token=") || cookie.startsWith("refresh_token="));
    }

    @Test
    void signUpWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content(SIGN_UP_BODY))
                .andExpect(status().isForbidden());
        verify(authService, never()).signUp(any());
    }

    private List<String> authCookies(String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("access_token=") || cookie.startsWith("refresh_token="))
                .toList();
    }
}
