package com.flytaxi.servicessepush.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flytaxi.internalCommon.request.PushRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Every SSE instance subscribes to the same Redis channel. A message published by any instance is
 * therefore delivered to each instance, and each instance pushes it to the connections it owns.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SseConnectionRegistry registry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            PushRequest request = objectMapper.readValue(message.getBody(), PushRequest.class);
            deliver(request);
        } catch (Exception exception) {
            log.warn("Could not handle SSE message; error={}", exception.getMessage());
        }
    }

    public void deliver(PushRequest request) {
        if (request == null || request.getIdentity() == null) {
            return;
        }
        if (request.getUserId() == null) {
            int delivered = registry.broadcast(request.getIdentity(), request.getEvent(), request.getContent());
            log.debug("SSE broadcast; identity={}, event={}, delivered={}",
                    request.getIdentity(), request.getEvent(), delivered);
            return;
        }
        registry.sendToUser(request.getUserId(), request.getIdentity(), request.getEvent(), request.getContent());
    }
}
