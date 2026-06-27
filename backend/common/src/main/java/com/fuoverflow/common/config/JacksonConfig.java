package com.fuoverflow.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer timestampTimezoneCustomizer() {
        return builder -> builder.serializerByType(Instant.class, new InstantVnSerializer());
    }

    private static class InstantVnSerializer extends JsonSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.atZone(VN_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }
}
