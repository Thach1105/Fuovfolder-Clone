package com.fuoverflow.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebStaticResourceConfig implements WebMvcConfigurer {
    private final FileStorageProperties properties;

    public WebStaticResourceConfig(FileStorageProperties properties) {
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
