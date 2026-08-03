package com.skillsprint.configuration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FlywayPaymentTransactionShapeTest {

    private static final Path RECONCILIATION_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V70__Reconcile_all_system_data_and_financials.sql");

    @Test
    void reconciliationPaymentsMustRespectPurposeShapeConstraint() throws IOException {
        String migration = Files.readString(RECONCILIATION_MIGRATION);

        assertAll(
                () -> assertTrue(migration.contains("v_subscription_plan_id, 'SUBSCRIPTION'"),
                        "Every subscription reconciliation payment must provide a plan_id"),
                () -> assertTrue(migration.contains("NULL, 'COIN_TOP_UP', 100000, 'COIN_100'"),
                        "The pending coin top-up must provide coin_amount and coin_package_key"),
                () -> assertTrue(!migration.contains("NULL, 'SUBSCRIPTION'"),
                        "A subscription payment must never use a NULL plan_id")
        );
    }
}
