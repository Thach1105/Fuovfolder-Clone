package com.fuoverflow.broadcast.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class BroadcastEmitterPool {

    private static final Logger log = LoggerFactory.getLogger(BroadcastEmitterPool.class);
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public BroadcastEmitterPool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
    }

    public void broadcast(BroadcastMessage message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("Failed to serialize broadcast message", e);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(message.eventType())
                        .data(json));
            } catch (IOException e) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public int activeCount() {
        return emitters.size();
    }
}
