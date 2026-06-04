package com.fuoverflow.coursera.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CourseraProperties.class)
public class CourseraModuleConfig {
}
