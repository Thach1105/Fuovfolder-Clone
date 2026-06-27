package com.fuoverflow.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {JacksonAutoConfiguration.class, JacksonConfig.class})
class JacksonConfigTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void instantShouldSerializeWithVietnamOffset() throws Exception {
        // 2026-06-28T00:00:00Z = 2026-06-28T07:00:00+07:00
        Instant instant = Instant.parse("2026-06-28T00:00:00Z");
        String json = objectMapper.writeValueAsString(instant);
        assertThat(json).contains("+07:00");
        assertThat(json).doesNotContain("Z\"");
    }

    @Test
    void instantShouldPreserveCorrectTime() throws Exception {
        Instant instant = Instant.parse("2026-06-28T00:00:00Z");
        String json = objectMapper.writeValueAsString(instant);
        // UTC midnight = 07:00 Vietnam time
        assertThat(json).contains("07:00:00");
    }
}
