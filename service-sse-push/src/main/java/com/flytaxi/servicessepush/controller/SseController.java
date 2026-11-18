package com.flytaxi.servicessepush.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flytaxi.internalCommon.constant.UserIdentity;
import com.flytaxi.internalCommon.request.PushRequest;
import com.flytaxi.internalCommon.util.SsePrefixUtils;
import com.flytaxi.servicessepush.service.SseAuthService;
import com.flytaxi.servicessepush.service.SseConnectionRegistry;
import com.flytaxi.servicessepush.service.SseRedisSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * SSE endpoint used by browsers to keep a push connection open.
 *
 * Connections are local to this JVM; push requests are published on Redis so every instance can
 * deliver to the connections it owns. That is what makes multiple instances work.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SseController {

    private final SseAuthService sseAuthService;
    private final SseConnectionRegistry sseConnectionRegistry;
    private final SseRedisSubscriber sseRedisSubscriber;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sse.channel:sse:push}")
    private String channel;

    /**
     * EventSource cannot send an Authorization header, so the token is passed as a query parameter
     * and validated by the server. The user id is resolved from the token, never trusted from the
     * client.
     */
    @GetMapping("/connect")
    public SseEmitter connect(@RequestParam(value = "token", required = false) String token,
                              HttpServletResponse response) {
        SseAuthService.AuthenticatedUser user = sseAuthService.authenticate(token);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        String key = SsePrefixUtils.generatorSseKey(user.userId(), user.identity());
        return sseConnectionRegistry.register(key, user.userId(), user.identity());
    }

    /**
     * Internal push endpoint. A null userId means broadcast to every online user with the given
     * identity, which is how a new order reaches all online drivers.
     */
    @PostMapping("/push")
    public String push(@RequestBody PushRequest pushRequest) {
        if (pushRequest == null || pushRequest.getIdentity() == null) {
            return "invalid push request";
        }
        if (!isKnownIdentity(pushRequest.getIdentity())) {
            return "invalid identity";
        }
        try {
            String payload = objectMapper.writeValueAsString(pushRequest);
            stringRedisTemplate.convertAndSend(channel, payload);
            return "published";
        } catch (Exception exception) {
            log.warn("SSE Redis publish failed; delivering locally, error={}", exception.getMessage());
            sseRedisSubscriber.deliver(pushRequest);
            return "delivered-locally";
        }
    }

    @GetMapping("/close")
    public String close(@RequestParam(value = "token", required = false) String token) {
        SseAuthService.AuthenticatedUser user = sseAuthService.authenticate(token);
        String key = SsePrefixUtils.generatorSseKey(user.userId(), user.identity());
        sseConnectionRegistry.remove(key);
        return "close successfully!";
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("connections", sseConnectionRegistry.size());
    }

    private boolean isKnownIdentity(String identity) {
        return UserIdentity.PASSENGER.getIdentity().equals(identity)
                || UserIdentity.DRIVER.getIdentity().equals(identity)
                || UserIdentity.ADMIN.getIdentity().equals(identity);
    }
}
