package com.fuoverflow.exam.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ExamProperties.class, ExamWebhookProperties.class})
public class ExamModuleConfig {
}
