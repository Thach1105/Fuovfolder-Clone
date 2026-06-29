package com.fuoverflow.broadcast.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.broadcast.persistence.BroadcastConfigEntity;
import com.fuoverflow.broadcast.persistence.BroadcastConfigRepository;
import com.fuoverflow.broadcast.persistence.BroadcastEventEntity;
import com.fuoverflow.broadcast.persistence.BroadcastEventRepository;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import com.fuoverflow.common.broadcast.BroadcastPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class BroadcastService {

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);
    private final BroadcastConfigRepository configRepo;
    private final BroadcastEventRepository eventRepo;
    private final BroadcastPublisher publisher;
    private final ObjectMapper objectMapper;

    public BroadcastService(BroadcastConfigRepository configRepo,
                            BroadcastEventRepository eventRepo,
                            BroadcastPublisher publisher,
                            ObjectMapper objectMapper) {
        this.configRepo = configRepo;
        this.eventRepo = eventRepo;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void evaluateAndBroadcast(String eventType, Map<String, Object> eventData) {
        BroadcastConfigEntity config = configRepo.findByEventType(eventType).orElse(null);
        if (config == null || !config.isEnabled()) {
            return;
        }

        Map<String, Object> configMap = parseJson(config.getConfigJson());
        BroadcastEvaluator evaluator = BroadcastEvaluatorFactory.get(eventType);
        if (evaluator == null) {
            log.warn("No evaluator registered for event type: {}", eventType);
            return;
        }

        String message = evaluator.evaluate(configMap, eventData);
        if (message == null) {
            return;
        }

        BroadcastMessage broadcast = new BroadcastMessage(eventType, message, eventData);
        publisher.publish(broadcast);
        logEvent(broadcast);
    }

    @Transactional
    public void logEvent(BroadcastMessage broadcast) {
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(broadcast.data());
        } catch (Exception e) {
            dataJson = "{}";
        }
        BroadcastEventEntity event = BroadcastEventEntity.create(
                broadcast.eventType(), broadcast.message(), dataJson);
        eventRepo.save(event);
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
