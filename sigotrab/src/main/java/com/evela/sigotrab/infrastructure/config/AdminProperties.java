package com.evela.sigotrab.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sigotrab.admin")
public record AdminProperties(String username, String password, String fullName) {
}
