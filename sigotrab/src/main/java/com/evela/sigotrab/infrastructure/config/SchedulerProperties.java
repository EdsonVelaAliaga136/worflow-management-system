package com.evela.sigotrab.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sigotrab.scheduler")
public record SchedulerProperties(long fixedDelayMs) {
}
