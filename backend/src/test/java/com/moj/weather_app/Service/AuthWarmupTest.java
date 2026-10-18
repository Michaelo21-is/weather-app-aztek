package com.moj.weather_app.Service;

import com.moj.weather_app.Configuration.JwtProperties;
import com.moj.weather_app.Dto.AuthTokens;
import com.moj.weather_app.Enum.TokenType;
import com.moj.weather_app.Repository.UsersRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthWarmupTest {
    private final AuthService authService = mock(AuthService.class);
    private final JwtService jwtService = new JwtService(new JwtProperties(
            "test-secret-test-secret-test-secret-123", Duration.ofMinutes(15), Duration.ofDays(14)));
    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final RedisService redisService = mock(RedisService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final AuthWarmup warmup = new AuthWarmup(authService, jwtService, usersRepository,
            new BCryptPasswordEncoder(4), redisService, transactionManager);

    @Test
    void signUpRunsInATransactionThatIsRolledBack() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        UUID userId = UUID.randomUUID();
        when(authService.signUp(any())).thenReturn(new AuthTokens(
                jwtService.generateToken(userId, TokenType.ACCESS), jwtService.generateToken(userId, TokenType.REFRESH)));

        warmup.run(null);

        verify(authService).signUp(any());
        // TransactionTemplate always ends with commit(); a real transaction manager rolls back a rollback-only status.
        ArgumentCaptor<TransactionStatus> status = ArgumentCaptor.forClass(TransactionStatus.class);
        verify(transactionManager).commit(status.capture());
        assertThat(status.getValue().isRollbackOnly()).isTrue();
        verify(usersRepository).findByEmail("warmup@warmup.invalid");
        verify(redisService).getRemainingDuration("warmup");
    }

    @Test
    void failuresNeverBlockStartup() {
        when(transactionManager.getTransaction(any())).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> warmup.run(null)).doesNotThrowAnyException();
    }
}
