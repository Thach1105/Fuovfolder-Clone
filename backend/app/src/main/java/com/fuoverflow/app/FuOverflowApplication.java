package com.fuoverflow.app;

import com.fuoverflow.common.config.ObjectStorageProperties;
import com.fuoverflow.common.config.UploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication(scanBasePackages = "com.fuoverflow")
@EnableConfigurationProperties({ObjectStorageProperties.class, UploadProperties.class})
@EnableJpaRepositories(basePackages = "com.fuoverflow")
@EntityScan(basePackages = "com.fuoverflow")
public class FuOverflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(FuOverflowApplication.class, args);
    }
}
