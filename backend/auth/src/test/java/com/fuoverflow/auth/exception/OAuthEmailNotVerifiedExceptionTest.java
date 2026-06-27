package com.fuoverflow.auth.exception;

import com.fuoverflow.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthEmailNotVerifiedExceptionTest {

    @Test
    void shouldBeApiExceptionWith403() {
        var ex = new OAuthEmailNotVerifiedException();
        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.code()).isEqualTo("OAUTH_EMAIL_NOT_VERIFIED");
    }
}
