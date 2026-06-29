package com.fuoverflow.broadcast.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import com.fuoverflow.common.broadcast.BroadcastPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisBroadcastPublisher implements BroadcastPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisBroadcastPublisher.class);
    public static final String CHANNEL = "broadcast:all";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisBroadcastPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(BroadcastMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHANNEL, json);
            log.debug("Published broadcast: type={}", message.eventType());
        } catch (Exception e) {
            log.error("Failed to publish broadcast: type={}", message.eventType(), e);
        }
    }
}
