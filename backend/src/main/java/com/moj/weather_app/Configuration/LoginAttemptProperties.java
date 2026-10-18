package com.moj.weather_app.Configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.login-attempt")
public record LoginAttemptProperties(
        @Min(1) int maxFailedAttempts,
        @NotNull Duration failedAttemptsTtl,
        @NotNull Duration initialBlockDuration,
        @Min(1) int blockDurationMultiplier,
        @NotNull Duration maxBlockDuration,
        @NotNull Duration blockHistoryTtl,
        @NotBlank String failedAttemptsKeyPrefix,
        @NotBlank String blockKeyPrefix,
        @NotBlank String blockHistoryKeyPrefix
) {
}
