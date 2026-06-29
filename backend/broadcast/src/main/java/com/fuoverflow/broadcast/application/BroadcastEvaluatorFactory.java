package com.fuoverflow.broadcast.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BroadcastEvaluatorFactory {

    private static final Map<String, BroadcastEvaluator> EVALUATORS = new ConcurrentHashMap<>();

    private BroadcastEvaluatorFactory() {}

    public static void register(String eventType, BroadcastEvaluator evaluator) {
        EVALUATORS.put(eventType, evaluator);
    }

    public static BroadcastEvaluator get(String eventType) {
        return EVALUATORS.get(eventType);
    }
}
