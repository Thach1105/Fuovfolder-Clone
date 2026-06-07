package com.fuoverflow.common.config;

import com.fuoverflow.common.forum.PostBodyFormatter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties({ObjectStorageProperties.class, UploadProperties.class})
public class PostBodyFormatterConfiguration {
    private final ObjectStorageProperties objectStorageProperties;

    public PostBodyFormatterConfiguration(ObjectStorageProperties objectStorageProperties) {
        this.objectStorageProperties = objectStorageProperties;
    }

    @PostConstruct
    void configureAllowedImagePrefixes() {
        List<String> prefixes = new ArrayList<>();
        prefixes.add(PostBodyFormatter.LEGACY_UPLOADS_PREFIX);
        ObjectStorageProperties.S3 s3 = objectStorageProperties.s3();
        if (s3 != null && s3.publicBaseUrl() != null && !s3.publicBaseUrl().isBlank()) {
            String base = s3.publicBaseUrl().replaceAll("/+$", "");
            prefixes.add(base + "/" + s3.bucket() + "/");
            if (s3.pathStyleAccessOrDefault()) {
                prefixes.add(base + "/");
            }
        }
        PostBodyFormatter.configureAllowedImageUrlPrefixes(prefixes);
    }
}
