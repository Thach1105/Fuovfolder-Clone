package com.fuoverflow.common.web;

import com.fuoverflow.common.config.SupportProperties;
import com.fuoverflow.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new SupportProperties("support@fuoverflow.com", "0900-000-000"));
    }

    @Test
    void shouldIncludeSupportContactIn5xxMessage() {
        var request = new MockHttpServletRequest();
        var response = handler.handleUnexpected(new RuntimeException("boom"), request);
        assertThat(response.getBody().message())
                .contains("support@fuoverflow.com")
                .contains("0900-000-000");
        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void shouldReturn429ForTooManyRequestsException() {
        var request = new MockHttpServletRequest();
        var ex = new TooManyRequestsException("RESEND_TOO_SOON", "Vui lòng chờ 60 giây.");
        var response = handler.handleApiException(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody().code()).isEqualTo("RESEND_TOO_SOON");
    }

    @Test
    void shouldWriteNothingWhenAsyncTimesOutAfterResponseCommitted() {
        var request = new MockHttpServletRequest("GET", "/api/v1/broadcasts/stream");
        var response = new MockHttpServletResponse();
        response.setCommitted(true);

        var result = handler.handleAsyncTimeout(new AsyncRequestTimeoutException(), request, response);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturn503WhenAsyncTimesOutBeforeResponseCommitted() {
        var request = new MockHttpServletRequest("GET", "/api/v1/broadcasts/stream");
        var response = new MockHttpServletResponse();

        var result = handler.handleAsyncTimeout(new AsyncRequestTimeoutException(), request, response);

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode().value()).isEqualTo(503);
        assertThat(result.getBody().code()).isEqualTo("ASYNC_REQUEST_TIMEOUT");
    }
}
