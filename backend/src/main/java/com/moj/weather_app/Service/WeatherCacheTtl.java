package com.moj.weather_app.Service;

import java.time.Duration;
import java.time.Instant;

/** Weather cache entries expire at the next full hour of the location's local time, so data refreshes hourly. */
public final class WeatherCacheTtl {
    private static final long SECONDS_PER_HOUR = 3600;

    private WeatherCacheTtl() {
    }

    /**
     * Time left until the next full hour in a timezone with the given UTC offset, e.g. 10:15 → 45 min,
     * 10:59 → 1 min. The offset matters for zones like UTC+5:30, where full hours differ from UTC.
     * Always between 1 second and 1 hour.
     */
    public static Duration untilNextFullHour(Instant now, int utcOffsetSeconds) {
        long localEpochSecond = now.getEpochSecond() + utcOffsetSeconds;
        long secondsIntoHour = Math.floorMod(localEpochSecond, SECONDS_PER_HOUR);
        return Duration.ofSeconds(SECONDS_PER_HOUR - secondsIntoHour);
    }
}
