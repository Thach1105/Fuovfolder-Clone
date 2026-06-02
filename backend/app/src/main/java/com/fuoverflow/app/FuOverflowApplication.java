package com.fuoverflow.app;

import com.fuoverflow.common.config.FileStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.fuoverflow")
@EnableConfigurationProperties(FileStorageProperties.class)
@EnableJpaRepositories(basePackages = "com.fuoverflow")
@EntityScan(basePackages = "com.fuoverflow")
public class FuOverflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(FuOverflowApplication.class, args);
    }
}
