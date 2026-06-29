package com.fuoverflow.broadcast.application;

import com.fuoverflow.common.broadcast.DepositCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class BroadcastEventHandler {

    private final BroadcastService broadcastService;

    public BroadcastEventHandler(BroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @Async
    @EventListener
    public void onDepositCompleted(DepositCompletedEvent event) {
        broadcastService.evaluateAndBroadcast(
                DepositBroadcastEvaluator.EVENT_TYPE,
                event.toEventData());
    }
}
