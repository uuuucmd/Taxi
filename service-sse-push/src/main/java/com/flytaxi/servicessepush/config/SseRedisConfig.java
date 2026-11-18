package com.flytaxi.servicessepush.config;

import com.flytaxi.servicessepush.service.SseRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class SseRedisConfig {

    @Bean
    public RedisMessageListenerContainer sseMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            SseRedisSubscriber subscriber,
            @Value("${sse.channel:sse:push}") String channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(channel));
        return container;
    }
}
