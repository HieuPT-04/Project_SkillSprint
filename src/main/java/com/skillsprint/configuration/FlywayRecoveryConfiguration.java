package com.skillsprint.configuration;

import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Performs a one-time, narrowly scoped recovery for the failed V55 seed
 * migration. Flyway validates before Spring creates any application bean, so a
 * failed V55 otherwise requires an operator to run {@code flyway repair} by
 * hand. Once V55 reruns successfully this strategy only calls migrate.
 */
@Slf4j
@Configuration
@Profile("prod")
public class FlywayRecoveryConfiguration {

    private static final String RECOVERABLE_VERSION = "55";

    @Bean
    FlywayMigrationStrategy recoverFailedV55BeforeMigrate() {
        return flyway -> {
            List<MigrationInfo> failedMigrations = Arrays.stream(flyway.info().all())
                    .filter(migration -> migration.getState() == MigrationState.FAILED)
                    .toList();

            if (failedMigrations.isEmpty()) {
                flyway.migrate();
                return;
            }

            boolean onlyV55Failed = failedMigrations.size() == 1
                    && RECOVERABLE_VERSION.equals(failedMigrations.get(0).getVersion().getVersion());
            if (!onlyV55Failed) {
                throw new IllegalStateException(
                        "Flyway has failed migrations outside the approved V55 recovery scope; refusing automatic repair");
            }

            log.warn("Removing failed Flyway V55 history entry before retrying the corrected seed migration");
            flyway.repair();
            flyway.migrate();
        };
    }
}
