package com.fuoverflow.common.config;

import com.fuoverflow.common.storage.LocalObjectStorage;
import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.common.storage.S3CompatibleObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "fuoverflow.storage.provider", havingValue = "local")
    ObjectStorage localObjectStorage(ObjectStorageProperties properties) {
        return new LocalObjectStorage(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "fuoverflow.storage.provider", havingValue = "s3", matchIfMissing = true)
    ObjectStorage s3CompatibleObjectStorage(ObjectStorageProperties properties) {
        S3CompatibleObjectStorage storage = new S3CompatibleObjectStorage(properties);
        storage.ensureBucketExists();
        return storage;
    }
}
