package com.moj.weather_app.Service;

import com.moj.weather_app.Dto.SignInDto;
import com.moj.weather_app.Dto.SignUpDto;
import com.moj.weather_app.Dto.AuthTokens;
import com.moj.weather_app.Entity.RefreshToken;
import com.moj.weather_app.Entity.Users;
import com.moj.weather_app.Enum.TokenType;
import com.moj.weather_app.Repository.RefreshTokenRepository;
import com.moj.weather_app.Repository.UsersRepository;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {
    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    public AuthService(UsersRepository usersRepository, PasswordEncoder passwordEncoder, LoginAttemptService loginAttemptService,
                       JwtService jwtService, RefreshTokenRepository refreshTokenRepository) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
    }
    public void signUp(SignUpDto signUpDto){
        if (usersRepository.existsByEmail(signUpDto.getEmail())){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        String encodedPassword = passwordEncoder.encode(signUpDto.getPassword());
        Users users = Users.builder().email(signUpDto.getEmail()).password(encodedPassword).favoriteCityCounter(0).nickname(signUpDto.getNickname()).build();
        try {
            usersRepository.save(users);
        }
        catch (Exception e){
            log.error("Failed to save user when sign up, error message: \n", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save user something went wrong, please try again later");
        }
    }
    public AuthTokens signIn(SignInDto signInDto){
        String email = signInDto.getEmail();
        loginAttemptService.checkBlocked();
        Users user = usersRepository.findByEmail(email).orElse(null);
        // BCrypt salts every hash, so passwords must be checked with matches(), not by re-encoding.
        if (user == null || !passwordEncoder.matches(signInDto.getPassword(), user.getPassword())){
            loginAttemptService.loginFailed();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        loginAttemptService.loginSucceeded();
        return issueTokens(user);
    }

    /**
     * Exchanges a valid refresh token for a new access/refresh token pair. The old refresh token
     * stops working because only the latest one is stored (rotation).
     */
    public AuthTokens refresh(String refreshToken){
        RefreshToken stored = findStoredRefreshToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        return issueTokens(stored.getUsers());
    }

    /** Revokes the refresh token, if it is still the current one. */
    public void signOut(String refreshToken){
        findStoredRefreshToken(refreshToken).ifPresent(refreshTokenRepository::delete);
    }

    private AuthTokens issueTokens(Users user){
        String accessToken = jwtService.generateToken(user.getId(), TokenType.ACCESS);
        String refreshToken = jwtService.generateToken(user.getId(), TokenType.REFRESH);
        RefreshToken stored = refreshTokenRepository.findByUsersId(user.getId())
                .orElseGet(() -> RefreshToken.builder().users(user).build());
        // Only a hash is stored, so a database leak does not expose usable refresh tokens.
        stored.setRefreshToken(hash(refreshToken));
        stored.setExpirationDate(Instant.now().plus(jwtService.getExpiration(TokenType.REFRESH)));
        refreshTokenRepository.save(stored);
        return new AuthTokens(accessToken, refreshToken);
    }

    /** Returns the stored record only if the token is a valid REFRESH token and matches the user's current one. */
    private Optional<RefreshToken> findStoredRefreshToken(String refreshToken){
        if (refreshToken == null){
            return Optional.empty();
        }
        UUID userId;
        try {
            userId = jwtService.extractUserId(refreshToken, TokenType.REFRESH);
        } catch (JwtException e){
            return Optional.empty();
        }
        byte[] tokenHash = hash(refreshToken).getBytes(StandardCharsets.UTF_8);
        return refreshTokenRepository.findByUsersId(userId)
                .filter(stored -> MessageDigest.isEqual(tokenHash, stored.getRefreshToken().getBytes(StandardCharsets.UTF_8)));
    }

    private static String hash(String token){
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e){
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
