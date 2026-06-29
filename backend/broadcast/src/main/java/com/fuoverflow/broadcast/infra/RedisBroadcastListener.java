package com.fuoverflow.broadcast.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class RedisBroadcastListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisBroadcastListener.class);
    private final BroadcastEmitterPool emitterPool;
    private final ObjectMapper objectMapper;

    public RedisBroadcastListener(BroadcastEmitterPool emitterPool, ObjectMapper objectMapper) {
        this.emitterPool = emitterPool;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            BroadcastMessage broadcast = objectMapper.readValue(message.getBody(), BroadcastMessage.class);
            emitterPool.broadcast(broadcast);
        } catch (Exception e) {
            log.error("Failed to process broadcast message from Redis", e);
        }
    }
}
