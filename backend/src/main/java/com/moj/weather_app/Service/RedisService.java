package com.moj.weather_app.Service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {
    // INCR and PEXPIRE run as one atomic script, so concurrent callers never lose an increment
    // and a counter can never be left behind without a TTL.
    private static final RedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) " +
            "redis.call('PEXPIRE', KEYS[1], ARGV[1]) " +
            "return count",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void uploadRedis(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /** Returns the value stored at the key, or {@code null} if it does not exist. */
    public String readRedis(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void removeRedis(String key) {
        redisTemplate.delete(key);
    }

    /**
     * Returns the remaining time to live of the key, or {@link Duration#ZERO} if the key
     * does not exist or has no expiry.
     */
    public Duration getRemainingDuration(String key) {
        Long millis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        return millis == null || millis <= 0 ? Duration.ZERO : Duration.ofMillis(millis);
    }

    /**
     * Atomically increments the counter stored at the key and (re)sets its TTL,
     * so the key expires {@code ttl} after the last increment.
     */
    public long incrementWithTtl(String key, Duration ttl) {
        Long count = redisTemplate.execute(INCREMENT_WITH_TTL_SCRIPT, List.of(key), String.valueOf(ttl.toMillis()));
        return count == null ? 0 : count;
    }
}
