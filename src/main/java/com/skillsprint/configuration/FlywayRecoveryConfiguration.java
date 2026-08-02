package com.skillsprint.configuration;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Performs automatic recovery before running Flyway migrations on production.
 * If a previous deployment left a failed migration entry or checksum mismatch
 * in flyway_schema_history, this strategy executes {@code flyway.repair()} first
 * so that {@code flyway.migrate()} can proceed smoothly.
 */
@Slf4j
@Configuration
@Profile("prod")
public class FlywayRecoveryConfiguration {

    @Bean
    FlywayMigrationStrategy recoverFailedV55BeforeMigrate() {
        return flyway -> {
            try {
                flyway.validate();
            } catch (Exception exception) {
                log.warn("Flyway validation failed ({}); executing automatic flyway.repair() before migration",
                        exception.getMessage());
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
