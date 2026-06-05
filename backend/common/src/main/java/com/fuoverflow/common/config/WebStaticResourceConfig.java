package com.fuoverflow.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@ConditionalOnProperty(name = "fuoverflow.storage.provider", havingValue = "local")
public class WebStaticResourceConfig implements WebMvcConfigurer {
    private final ObjectStorageProperties properties;

    public WebStaticResourceConfig(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadsPath = properties.uploadsPath();
        if (uploadsPath == null || uploadsPath.isBlank()) {
            return;
        }
        String location = Path.of(uploadsPath).toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
