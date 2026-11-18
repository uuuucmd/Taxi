package com.flytaxi.servicessepush.service;

import com.flytaxi.internalCommon.util.SsePrefixUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps the live SSE connections held by this JVM.
 *
 * The map is deliberately local to one process. Cross-instance delivery is handled by the Redis
 * pub/sub channel, which fans a message out to every instance and lets each one push to its own
 * connections.
 */
@Slf4j
@Component
public class SseConnectionRegistry {

    private final Map<String, SseConnection> connections = new ConcurrentHashMap<>();
    private final AtomicLong eventIds = new AtomicLong();

    @Value("${sse.timeout-ms:1800000}")
    private long timeoutMs;

    public SseEmitter register(String key, Long userId, String identity) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> {
            remove(key, emitter);
            completeQuietly(emitter);
        });
        emitter.onError(error -> remove(key, emitter));

        SseConnection previous = connections.put(key, new SseConnection(key, userId, identity, emitter));
        if (previous != null && previous.emitter != emitter) {
            // A refresh or a second tab replaces the old connection; close it instead of leaking it.
            completeQuietly(previous.emitter);
        }

        send(emitter, key, null, "connected");
        log.info("SSE connected; key={}, online={}", key, connections.size());
        return emitter;
    }

    public void remove(String key, SseEmitter emitter) {
        connections.computeIfPresent(key, (currentKey, current) ->
                current.emitter == emitter ? null : current);
    }

    public boolean remove(String key) {
        SseConnection removed = connections.remove(key);
        if (removed == null) {
            return false;
        }
        completeQuietly(removed.emitter);
        return true;
    }

    public void sendToUser(Long userId, String identity, String event, String content) {
        if (userId == null || identity == null) {
            return;
        }
        String key = SsePrefixUtils.generatorSseKey(userId, identity);
        SseConnection connection = connections.get(key);
        if (connection == null) {
            log.debug("SSE user is offline; key={}, event={}", key, event);
            return;
        }
        send(connection.emitter, key, event, content);
    }

    public int broadcast(String identity, String event, String content) {
        int delivered = 0;
        for (SseConnection connection : connections.values()) {
            if (identity != null && !identity.equals(connection.identity)) {
                continue;
            }
            if (send(connection.emitter, connection.key, event, content)) {
                delivered++;
            }
        }
        return delivered;
    }

    public int size() {
        return connections.size();
    }

    @Scheduled(fixedDelayString = "${sse.heartbeat-ms:20000}")
    public void heartbeat() {
        for (SseConnection connection : connections.values()) {
            try {
                connection.emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception exception) {
                remove(connection.key, connection.emitter);
                completeQuietly(connection.emitter);
            }
        }
    }

    private boolean send(SseEmitter emitter, String key, String event, String content) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .id(String.valueOf(eventIds.incrementAndGet()));
            if (event != null && !event.isBlank()) {
                builder.name(event);
            }
            emitter.send(builder.data(content == null ? "" : content));
            return true;
        } catch (Exception exception) {
            log.debug("SSE send failed; key={}, event={}, error={}", key, event,
                    exception.getClass().getSimpleName());
            remove(key, emitter);
            completeQuietly(emitter);
            return false;
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // The connection may already be closed by the container.
        }
    }

    private static final class SseConnection {
        private final String key;
        private final Long userId;
        private final String identity;
        private final SseEmitter emitter;

        private SseConnection(String key, Long userId, String identity, SseEmitter emitter) {
            this.key = key;
            this.userId = userId;
            this.identity = identity;
            this.emitter = emitter;
        }
    }
}
