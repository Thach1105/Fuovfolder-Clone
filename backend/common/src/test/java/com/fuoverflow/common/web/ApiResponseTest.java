package com.fuoverflow.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void okWrapsPayloadWithDefaults() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isNull();
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.error()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void okWithMessageSetsMessage() {
        ApiResponse<String> response = ApiResponse.ok("payload", "done");

        assertThat(response.message()).isEqualTo("done");
        assertThat(response.success()).isTrue();
    }

    @Test
    void failureSetsErrorAndNullData() {
        ApiResponse.ErrorDetail detail = new ApiResponse.ErrorDetail(
                "trace-1",
                List.of(new ApiResponse.ErrorDetail.FieldError("email", "must not be blank")));

        ApiResponse<Void> response = ApiResponse.failure("VALIDATION_ERROR", "Request validation failed", detail);

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo(detail);
    }

    @Test
    void successJsonOmitsNullMessageAndError() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.ok("payload"));

        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"code\":\"OK\"");
        assertThat(json).contains("\"data\":\"payload\"");
        assertThat(json).doesNotContain("\"message\"");
        assertThat(json).doesNotContain("\"error\"");
    }

    @Test
    void errorJsonKeepsSuccessFalseAndOmitsNullData() throws Exception {
        ApiResponse.ErrorDetail detail = new ApiResponse.ErrorDetail("trace-1", null);
        String json = mapper.writeValueAsString(ApiResponse.failure("INTERNAL_ERROR", "boom", detail));

        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"code\":\"INTERNAL_ERROR\"");
        assertThat(json).doesNotContain("\"data\"");
        assertThat(json).doesNotContain("\"fields\"");
    }
}
