package com.fuoverflow.broadcast.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BroadcastEmitterPoolTest {

    private BroadcastEmitterPool pool;

    @BeforeEach
    void setUp() {
        pool = new BroadcastEmitterPool(new ObjectMapper());
    }

    @Test
    void register_increasesActiveCount() {
        assertEquals(0, pool.activeCount());
        pool.register(new SseEmitter());
        assertEquals(1, pool.activeCount());
    }

    @Test
    void register_multipleEmitters_incrementsCount() {
        pool.register(new SseEmitter());
        pool.register(new SseEmitter());
        pool.register(new SseEmitter());
        assertEquals(3, pool.activeCount());
    }

    @Test
    void completedEmitter_isRemovedFromPool() {
        // Use a mock SseEmitter so we can capture and invoke the onCompletion runnable
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);

        pool.register(emitter);
        verify(emitter).onCompletion(completionCaptor.capture());
        assertEquals(1, pool.activeCount());

        // Simulate the servlet container triggering the completion callback
        completionCaptor.getValue().run();

        assertEquals(0, pool.activeCount());
    }

    @Test
    void broadcast_doesNotThrow_whenPoolEmpty() {
        BroadcastMessage msg = new BroadcastMessage("test", "hello", Map.of());
        assertDoesNotThrow(() -> pool.broadcast(msg));
    }

    @Test
    void broadcast_doesNotThrow_withRegisteredEmitter() {
        // SseEmitter in tests without servlet container; send() throws IOException
        // BroadcastEmitterPool catches IOException and calls completeWithError
        SseEmitter emitter = new SseEmitter();
        pool.register(emitter);

        BroadcastMessage msg = new BroadcastMessage("deposit.completed", "test message",
                Map.of("amountVnd", 1_000_000));

        assertDoesNotThrow(() -> pool.broadcast(msg));
    }

    @Test
    void activeCount_returnsZero_afterAllCompleted() {
        SseEmitter e1 = mock(SseEmitter.class);
        SseEmitter e2 = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> cap1 = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> cap2 = ArgumentCaptor.forClass(Runnable.class);

        pool.register(e1);
        pool.register(e2);
        verify(e1).onCompletion(cap1.capture());
        verify(e2).onCompletion(cap2.capture());
        assertEquals(2, pool.activeCount());

        cap1.getValue().run();
        cap2.getValue().run();

        assertEquals(0, pool.activeCount());
    }
}
