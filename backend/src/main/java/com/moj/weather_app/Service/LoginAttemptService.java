package com.moj.weather_app.Service;

import com.moj.weather_app.Configuration.LoginAttemptProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Service
public class LoginAttemptService {
    private final RedisService redisService;
    private final LoginAttemptProperties properties;
    // Spring injects a proxy that resolves to the HTTP request being handled by the current thread.
    private final HttpServletRequest request;

    public LoginAttemptService(RedisService redisService, LoginAttemptProperties properties, HttpServletRequest request) {
        this.redisService = redisService;
        this.properties = properties;
        this.request = request;
    }

    /** Throws 429 if the client IP is currently blocked. */
    public void checkBlocked() {
        Duration remaining = redisService.getRemainingDuration(blockKey());
        if (!remaining.isZero()) {
            throw tooManyAttempts(remaining);
        }
    }

    /**
     * Records a failed login for the client IP. When the limit is reached the IP is blocked, the failed
     * attempt counter is cleared and 429 is thrown; otherwise the method returns normally.
     */
    public void loginFailed() {
        String ip = clientIp();
        long attempts = redisService.incrementWithTtl(attemptsKey(ip), properties.failedAttemptsTtl());
        // The increment is atomic, so exactly one concurrent request observes the limit
        // and creates the block; the others just fail.
        if (attempts != properties.maxFailedAttempts()) {
            return;
        }
        long blockCount = redisService.incrementWithTtl(blockHistoryKey(ip), properties.blockHistoryTtl());
        Duration blockDuration = blockDurationFor(blockCount);
        redisService.uploadRedis(blockKey(ip), String.valueOf(blockCount), blockDuration);
        redisService.removeRedis(attemptsKey(ip));
        throw tooManyAttempts(redisService.getRemainingDuration(blockKey(ip)));
    }

    /** Clears the failed attempt counter. Block history is kept so repeat offenders still escalate. */
    public void loginSucceeded() {
        redisService.removeRedis(attemptsKey(clientIp()));
    }

    private Duration blockDurationFor(long blockCount) {
        Duration duration = properties.initialBlockDuration();
        for (long i = 1; i < blockCount && duration.compareTo(properties.maxBlockDuration()) < 0; i++) {
            duration = duration.multipliedBy(properties.blockDurationMultiplier());
        }
        return duration.compareTo(properties.maxBlockDuration()) > 0 ? properties.maxBlockDuration() : duration;
    }

    private ResponseStatusException tooManyAttempts(Duration remaining) {
        // Round up so the user never sees "0 minutes and 0 seconds" while still blocked.
        long totalSeconds = Math.max(1, (remaining.toMillis() + 999) / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many failed login attempts. Try again in "
                        + minutes + " minutes and " + seconds + " seconds."
        );
    }

    /**
     * Uses the TCP peer address rather than reading X-Forwarded-For directly, since clients can forge
     * that header to dodge the block. Behind a trusted reverse proxy, set server.forward-headers-strategy
     * so that getRemoteAddr() returns the real client IP.
     */
    private String clientIp() {
        return request.getRemoteAddr();
    }

    private String attemptsKey(String ip) {
        return properties.failedAttemptsKeyPrefix() + ip;
    }

    private String blockKey() {
        return blockKey(clientIp());
    }

    private String blockKey(String ip) {
        return properties.blockKeyPrefix() + ip;
    }

    private String blockHistoryKey(String ip) {
        return properties.blockHistoryKeyPrefix() + ip;
    }
}
