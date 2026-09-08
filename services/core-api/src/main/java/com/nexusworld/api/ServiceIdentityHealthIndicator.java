package com.nexusworld.api;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("serviceIdentity")
public class ServiceIdentityHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        return Health.up()
                .withDetail("service", "core-api")
                .withDetail("version", "dev")
                .build();
    }
}

