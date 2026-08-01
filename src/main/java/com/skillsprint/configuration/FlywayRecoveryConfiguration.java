package com.skillsprint.configuration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
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
    private static final int RECOVERABLE_APPLIED_CHECKSUM = 1372296114;

    @Bean
    FlywayMigrationStrategy recoverFailedV55BeforeMigrate() {
        return flyway -> {
            MigrationInfo[] migrations = flyway.info().all();
            List<MigrationInfo> failedMigrations = Arrays.stream(migrations)
                    .filter(migration -> migration.getState() == MigrationState.FAILED)
                    .toList();
            boolean hasApprovedV55ChecksumMismatch = hasApprovedV55ChecksumMismatch(flyway, migrations);

            if (failedMigrations.isEmpty() && !hasApprovedV55ChecksumMismatch) {
                flyway.migrate();
                return;
            }

            boolean onlyV55Failed = failedMigrations.size() == 1
                    && RECOVERABLE_VERSION.equals(failedMigrations.get(0).getVersion().getVersion());
            boolean onlyApprovedV55Mismatch = failedMigrations.isEmpty() && hasApprovedV55ChecksumMismatch;
            if (!onlyV55Failed && !onlyApprovedV55Mismatch) {
                throw new IllegalStateException(
                        "Flyway has failed migrations outside the approved V55 recovery scope; refusing automatic repair");
            }

            log.warn("Removing failed Flyway V55 history entry before retrying the corrected seed migration");
            flyway.repair();
            flyway.migrate();
        };
    }

    private boolean hasApprovedV55ChecksumMismatch(Flyway flyway, MigrationInfo[] migrations) {
        Integer resolvedChecksum = Arrays.stream(migrations)
                .filter(migration -> migration.getVersion() != null
                        && RECOVERABLE_VERSION.equals(migration.getVersion().getVersion()))
                .map(MigrationInfo::getChecksum)
                .filter(checksum -> checksum != null)
                .findFirst()
                .orElse(null);

        if (resolvedChecksum == null || resolvedChecksum == RECOVERABLE_APPLIED_CHECKSUM) {
            return false;
        }

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT checksum FROM flyway_schema_history WHERE version = ? AND success = TRUE")) {
            statement.setString(1, RECOVERABLE_VERSION);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt("checksum") == RECOVERABLE_APPLIED_CHECKSUM;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect Flyway V55 history for the approved recovery", exception);
        }
    }
}
