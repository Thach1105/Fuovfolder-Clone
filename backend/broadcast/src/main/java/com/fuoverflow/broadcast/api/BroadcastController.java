package com.fuoverflow.broadcast.api;

import com.fuoverflow.broadcast.application.AnnouncementService;
import com.fuoverflow.broadcast.infra.BroadcastEmitterPool;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/broadcasts")
public class BroadcastController {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final BroadcastEmitterPool emitterPool;
    private final AnnouncementService announcementService;

    public BroadcastController(BroadcastEmitterPool emitterPool,
                               AnnouncementService announcementService) {
        this.emitterPool = emitterPool;
        this.announcementService = announcementService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitterPool.register(emitter);
        announcementService.sendInitialState(emitter);
        return emitter;
    }
}
