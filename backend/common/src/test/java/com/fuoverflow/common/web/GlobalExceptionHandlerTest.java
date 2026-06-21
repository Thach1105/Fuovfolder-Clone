package com.fuoverflow.common.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void handleUnexpected_shouldLogUnhandledExceptionWithRequestContext() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        RuntimeException boom = new RuntimeException("boom");
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/api/v1/payment/create");
            when(request.getHeader("X-Request-Id")).thenReturn("req-123");

            var response = handler.handleUnexpected(boom, request);

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list.getLast().getFormattedMessage())
                    .contains("Unhandled exception")
                    .contains("POST")
                    .contains("/api/v1/payment/create")
                    .contains("req-123");
            assertThat(appender.list.getLast().getThrowableProxy().getMessage()).contains("boom");
        } finally {
            logger.detachAppender(appender);
        }
    }
}
