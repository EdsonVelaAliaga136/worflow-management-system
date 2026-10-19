package com.evela.sigotrab.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sigotrab.lock")
public record LockProperties(long durationMinutes) {
}
