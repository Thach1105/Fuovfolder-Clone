package com.fuoverflow.broadcast.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DepositBroadcastEvaluatorTest {

    private DepositBroadcastEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new DepositBroadcastEvaluator();
    }

    @Test
    void returnsMessage_whenAmountExceedsThreshold() {
        Map<String, Object> config = Map.of("thresholdVnd", 500_000);
        Map<String, Object> eventData = Map.of(
                "amountVnd", 1_000_000,
                "displayName", "Nguyen Van A");

        String result = evaluator.evaluate(config, eventData);

        assertNotNull(result);
        assertTrue(result.contains("Nguyen Van A"));
        assertTrue(result.contains("1,000,000"));
    }

    @Test
    void returnsNull_whenAmountBelowThreshold() {
        Map<String, Object> config = Map.of("thresholdVnd", 500_000);
        Map<String, Object> eventData = Map.of("amountVnd", 100_000);

        assertNull(evaluator.evaluate(config, eventData));
    }

    @Test
    void returnsMessage_whenAmountEqualsThreshold() {
        // evaluator uses strict < so amount == threshold is NOT filtered out
        Map<String, Object> config = Map.of("thresholdVnd", 500_000);
        Map<String, Object> eventData = Map.of("amountVnd", 500_000);

        String result = evaluator.evaluate(config, eventData);

        assertNotNull(result);
    }

    @Test
    void usesCustomTemplate_whenProvided() {
        Map<String, Object> config = Map.of(
                "thresholdVnd", 0,
                "messageTemplate", "Chúc mừng %s đã nạp %,d VND!");
        Map<String, Object> eventData = Map.of(
                "amountVnd", 200_000,
                "displayName", "Test User");

        String result = evaluator.evaluate(config, eventData);

        assertNotNull(result);
        assertTrue(result.startsWith("Chúc mừng Test User"));
    }

    @Test
    void usesDefaultDisplayName_whenMissing() {
        Map<String, Object> config = Map.of("thresholdVnd", 0);
        Map<String, Object> eventData = Map.of("amountVnd", 100_000);

        String result = evaluator.evaluate(config, eventData);

        assertNotNull(result);
        assertTrue(result.contains("Một thành viên"));
    }

    @Test
    void handlesStringValues_forThresholdAndAmount() {
        Map<String, Object> config = Map.of("thresholdVnd", "100000");
        Map<String, Object> eventData = Map.of(
                "amountVnd", "500000",
                "displayName", "String User");

        String result = evaluator.evaluate(config, eventData);

        assertNotNull(result);
        assertTrue(result.contains("String User"));
    }

    @Test
    void handlesNonNumericStringAmount_returnsMessage_withZeroAmount() {
        Map<String, Object> config = Map.of("thresholdVnd", 0);
        Map<String, Object> eventData = Map.of("amountVnd", "not-a-number");

        // toLong("not-a-number") -> 0L, threshold=0, 0 < 0 is false => returns message
        String result = evaluator.evaluate(config, eventData);

        assertNotNull(result);
    }

    @Test
    void handlesMissingKeys_gracefully() {
        // Empty config, empty eventData → threshold=0, amount=0 → 0 < 0 false → returns message
        Map<String, Object> config = Map.of();
        Map<String, Object> eventData = Map.of();

        String result = evaluator.evaluate(config, eventData);

        assertNotNull(result);
        assertTrue(result.contains("Một thành viên"));
    }
}
