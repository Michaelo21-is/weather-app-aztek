package com.moj.weather_app.Service;

import com.moj.weather_app.Configuration.JwtProperties;
import com.moj.weather_app.Dto.AuthTokens;
import com.moj.weather_app.Dto.SignInDto;
import com.moj.weather_app.Dto.SignUpDto;
import com.moj.weather_app.Entity.RefreshToken;
import com.moj.weather_app.Entity.Users;
import com.moj.weather_app.Enum.TokenType;
import com.moj.weather_app.Repository.RefreshTokenRepository;
import com.moj.weather_app.Repository.UsersRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private static final UUID USER_ID = UUID.randomUUID();

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService = new JwtService(new JwtProperties(
            "test-secret-test-secret-test-secret-123", Duration.ofMinutes(15), Duration.ofDays(14)));
    private final AuthService authService =
            new AuthService(usersRepository, passwordEncoder, loginAttemptService, jwtService, refreshTokenRepository);

    @Test
    void signUpSavesUserAndIssuesTheSameTokensAsSignIn() {
        when(usersRepository.saveAndFlush(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        AuthTokens tokens = authService.signUp(new SignUpDto("new@example.com", "Newbie", "secret123"));

        ArgumentCaptor<Users> savedUser = ArgumentCaptor.forClass(Users.class);
        verify(usersRepository).saveAndFlush(savedUser.capture());
        assertThat(savedUser.getValue().getNickname()).isEqualTo("Newbie");
        assertThat(passwordEncoder.matches("secret123", savedUser.getValue().getPassword())).isTrue();

        assertThat(jwtService.extractUserId(tokens.accessToken(), TokenType.ACCESS)).isEqualTo(USER_ID);
        assertThat(jwtService.extractUserId(tokens.refreshToken(), TokenType.REFRESH)).isEqualTo(USER_ID);
        RefreshToken stored = storedRefreshToken();
        assertThat(stored.getUsers().getId()).isEqualTo(USER_ID);
        assertThat(stored.getRefreshToken()).isNotEqualTo(tokens.refreshToken()).hasSize(64);
        assertThat(stored.getExpirationDate()).isCloseTo(Instant.now().plus(Duration.ofDays(14)), within(Duration.ofMinutes(1)));
        // Sign-up is not a login attempt, so it never touches the brute-force counters.
        verify(loginAttemptService, never()).loginSucceeded();
    }

    @Test
    void refreshTokenFromSignUpCanBeRotated() {
        when(usersRepository.saveAndFlush(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        AuthTokens signUpTokens = authService.signUp(new SignUpDto("new@example.com", "Newbie", "secret123"));
        RefreshToken stored = storedRefreshToken();
        when(refreshTokenRepository.findByUsersId(USER_ID)).thenReturn(Optional.of(stored));

        AuthTokens refreshed = authService.refresh(signUpTokens.refreshToken());

        assertThat(jwtService.extractUserId(refreshed.accessToken(), TokenType.ACCESS)).isEqualTo(USER_ID);
        assertThat(refreshed.refreshToken()).isNotEqualTo(signUpTokens.refreshToken());
    }

    @Test
    void signInStillIssuesTokens() {
        Users user = withId(Users.builder().email("user@example.com").nickname("User")
                .password(passwordEncoder.encode("secret123")).build());
        when(usersRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        AuthTokens tokens = authService.signIn(new SignInDto("user@example.com", null, "secret123"));

        assertThat(jwtService.extractUserId(tokens.accessToken(), TokenType.ACCESS)).isEqualTo(USER_ID);
        assertThat(storedRefreshToken().getUsers()).isSameAs(user);
        verify(loginAttemptService).loginSucceeded();
    }

    @Test
    void duplicateEmailIsRejectedWithoutIssuingTokens() {
        when(usersRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signUp(new SignUpDto("taken@example.com", "Taken", "secret123")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(usersRepository, never()).saveAndFlush(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void failedSaveIsReportedWithoutIssuingTokens() {
        when(usersRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> authService.signUp(new SignUpDto("new@example.com", "Newbie", "secret123")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
        verify(refreshTokenRepository, never()).save(any());
    }

    private RefreshToken storedRefreshToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Users withId(Users user) {
        user.setId(USER_ID);
        return user;
    }
}
