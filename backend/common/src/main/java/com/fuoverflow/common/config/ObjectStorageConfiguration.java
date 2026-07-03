package com.fuoverflow.common.config;

import com.fuoverflow.common.storage.LocalObjectStorage;
import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.common.storage.S3CompatibleObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ObjectStorageProperties.class, UploadProperties.class})
public class ObjectStorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "fuexam.storage.provider", havingValue = "local")
    ObjectStorage localObjectStorage(ObjectStorageProperties properties, UploadProperties uploadProperties) {
        return new LocalObjectStorage(properties, uploadProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "fuexam.storage.provider", havingValue = "s3", matchIfMissing = true)
    ObjectStorage s3CompatibleObjectStorage(ObjectStorageProperties properties, UploadProperties uploadProperties) {
        S3CompatibleObjectStorage storage = new S3CompatibleObjectStorage(properties, uploadProperties);
        storage.ensureBucketExists();
        return storage;
    }
}
