package com.fuoverflow.coursera.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.coursera.domain.RequestStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestStatusTransitionTest {
    @Test
    void allowsPendingToInProgress() {
        assertDoesNotThrow(() -> RequestStatusTransition.validate(RequestStatus.PENDING, RequestStatus.IN_PROGRESS));
    }

    @Test
    void rejectsCompletedToPending() {
        assertThrows(BadRequestException.class,
                () -> RequestStatusTransition.validate(RequestStatus.COMPLETED, RequestStatus.PENDING));
    }

    @Test
    void allowsInProgressToCancelled() {
        assertDoesNotThrow(() -> RequestStatusTransition.validate(RequestStatus.IN_PROGRESS, RequestStatus.CANCELLED));
    }

    @Test
    void allowedNextFromPending() {
        var next = RequestStatusTransition.allowedNextDb(RequestStatus.PENDING);
        org.junit.jupiter.api.Assertions.assertTrue(next.contains("in_progress"));
        org.junit.jupiter.api.Assertions.assertTrue(next.contains("cancelled"));
    }
}
