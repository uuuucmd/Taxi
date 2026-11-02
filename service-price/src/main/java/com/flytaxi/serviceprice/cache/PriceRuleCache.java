package com.flytaxi.serviceprice.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flytaxi.internalCommon.dto.PriceRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis cache for the latest pricing rule of a fare type.
 *
 * Redis is only a cache: the database remains the source of truth. Any Redis
 * failure is treated as a cache miss so pricing rule reads can continue.
 */
@Slf4j
@Component
public class PriceRuleCache {

    private static final String KEY_PREFIX = "price:rule:latest:";
    private static final String NOT_EXISTS_MARKER = "__NOT_EXISTS__";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${price.rule.cache-ttl-seconds:3600}")
    private long ttlSeconds;

    @Value("${price.rule.cache-not-exists-ttl-seconds:60}")
    private long notExistsTtlSeconds;

    public Lookup find(String fareType) {
        if (fareType == null || fareType.isBlank()) {
            return Lookup.miss();
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(key(fareType));
            if (value == null) {
                return Lookup.miss();
            }
            if (NOT_EXISTS_MARKER.equals(value)) {
                return Lookup.notExists();
            }
            return Lookup.hit(objectMapper.readValue(value, PriceRule.class));
        } catch (Exception exception) {
            log.warn("Price rule cache read failed; fareType={}, reason={}",
                    fareType, exception.getMessage());
            return Lookup.miss();
        }
    }

    public void put(String fareType, PriceRule priceRule) {
        if (fareType == null || fareType.isBlank() || priceRule == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    key(fareType),
                    objectMapper.writeValueAsString(priceRule),
                    Math.max(1L, ttlSeconds),
                    TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("Price rule cache write failed; fareType={}, reason={}",
                    fareType, exception.getMessage());
        }
    }

    public void putNotExists(String fareType) {
        if (fareType == null || fareType.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    key(fareType),
                    NOT_EXISTS_MARKER,
                    Math.max(1L, notExistsTtlSeconds),
                    TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("Price rule cache write failed; fareType={}, reason={}",
                    fareType, exception.getMessage());
        }
    }

    public void evict(String fareType) {
        if (fareType == null || fareType.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(key(fareType));
        } catch (Exception exception) {
            log.warn("Price rule cache eviction failed; fareType={}, reason={}",
                    fareType, exception.getMessage());
        }
    }

    private String key(String fareType) {
        return KEY_PREFIX + fareType;
    }

    public static final class Lookup {

        private final boolean hit;
        private final boolean notExists;
        private final PriceRule priceRule;

        private Lookup(boolean hit, boolean notExists, PriceRule priceRule) {
            this.hit = hit;
            this.notExists = notExists;
            this.priceRule = priceRule;
        }

        public static Lookup miss() {
            return new Lookup(false, false, null);
        }

        public static Lookup hit(PriceRule priceRule) {
            return new Lookup(true, false, priceRule);
        }

        public static Lookup notExists() {
            return new Lookup(false, true, null);
        }

        public boolean isHit() {
            return hit;
        }

        public boolean isNotExists() {
            return notExists;
        }

        public PriceRule getPriceRule() {
            return priceRule;
        }
    }
}
