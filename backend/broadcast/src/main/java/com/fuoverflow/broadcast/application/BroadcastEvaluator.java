package com.fuoverflow.broadcast.application;

import java.util.Map;

public interface BroadcastEvaluator {
    String evaluate(Map<String, Object> config, Map<String, Object> eventData);
}
