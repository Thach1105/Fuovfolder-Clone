package com.fuoverflow.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class TooManyRequestsExceptionTest {

    @Test
    void shouldReturn429Status() {
        var ex = new TooManyRequestsException("RESEND_TOO_SOON", "Please wait 60 seconds.");
        assertThat(ex.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.code()).isEqualTo("RESEND_TOO_SOON");
        assertThat(ex.getMessage()).isEqualTo("Please wait 60 seconds.");
    }
}
