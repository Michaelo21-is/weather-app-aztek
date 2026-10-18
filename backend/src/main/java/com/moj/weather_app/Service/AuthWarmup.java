package com.moj.weather_app.Service;

import com.moj.weather_app.Dto.AuthTokens;
import com.moj.weather_app.Dto.SignUpDto;
import com.moj.weather_app.Enum.TokenType;
import com.moj.weather_app.Repository.UsersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the sign-up and sign-in code paths once at startup. Their first run loads and compiles a lot of code
 * (Hibernate inserts, JJWT, BCrypt, the Redis client), which on the production plan's single slow vCPU took over
 * 10 seconds and landed on the first user to sign up after every restart. Runners finish before the readiness
 * probe reports UP. Nothing is kept: the sign-up runs in a transaction that is always rolled back.
 */
@Component
@Slf4j
public class AuthWarmup implements ApplicationRunner {
    // The .invalid TLD is reserved, so this can never be a real user's email.
    private static final String EMAIL = "warmup@warmup.invalid";
    private static final String PASSWORD = "warmup-password";

    private final AuthService authService;
    private final JwtService jwtService;
    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisService redisService;
    private final TransactionTemplate transactionTemplate;

    public AuthWarmup(AuthService authService, JwtService jwtService, UsersRepository usersRepository,
                      PasswordEncoder passwordEncoder, RedisService redisService,
                      PlatformTransactionManager transactionManager) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisService = redisService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.nanoTime();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                status.setRollbackOnly();
                AuthTokens tokens = authService.signUp(new SignUpDto(EMAIL, "warmup", PASSWORD));
                jwtService.extractUserId(tokens.accessToken(), TokenType.ACCESS);
            });
            // Sign-in's own paths (LoginAttemptService needs an HTTP request, so its Redis calls are made directly).
            usersRepository.findByEmail(EMAIL);
            passwordEncoder.matches(PASSWORD, passwordEncoder.encode(PASSWORD));
            redisService.getRemainingDuration("warmup");
            log.info("Auth warm-up finished in {} ms", (System.nanoTime() - start) / 1_000_000);
        } catch (RuntimeException e) {
            // Never block startup: without the warm-up only the first sign-up or sign-in is slower.
            log.warn("Auth warm-up failed, the first sign-up or sign-in may be slow: {}", e.toString());
        }
    }
}
