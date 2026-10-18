package com.moj.weather_app.Service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherCacheTtlTest {

    @ParameterizedTest
    @CsvSource({
            // utc time,               offset seconds, expected ttl
            "2026-10-17T10:15:00Z,     0,      PT45M",
            "2026-10-17T10:40:00Z,     0,      PT20M",
            "2026-10-17T10:59:00Z,     0,      PT1M",
            "2026-10-17T10:59:59Z,     0,      PT1S",
            "2026-10-17T10:00:00Z,     0,      PT1H",
            // Whole-hour offsets do not change the minutes: 13:15 in UTC+3.
            "2026-10-17T10:15:00Z,     10800,  PT45M",
            // UTC+5:30 (India): 10:15 UTC is 15:45 local.
            "2026-10-17T10:15:00Z,     19800,  PT15M",
            // UTC+5:45 (Nepal): 10:15 UTC is 16:00 local.
            "2026-10-17T10:15:00Z,     20700,  PT1H",
            // UTC-3:30 (Newfoundland): 10:15 UTC is 06:45 local.
            "2026-10-17T10:15:00Z,     -12600, PT15M"
    })
    void expiresAtNextFullLocalHour(String now, int offsetSeconds, String expected) {
        assertThat(WeatherCacheTtl.untilNextFullHour(Instant.parse(now), offsetSeconds))
                .isEqualTo(Duration.parse(expected));
    }
}
