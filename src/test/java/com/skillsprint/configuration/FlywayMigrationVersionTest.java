package com.skillsprint.configuration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FlywayMigrationVersionTest {

    private static final Pattern VERSIONED_SQL_MIGRATION = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final Path MIGRATION_DIRECTORY = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void versionedMigrationsMustUseUniqueVersions() throws IOException {
        Map<String, List<String>> migrationsByVersion;

        try (var migrationFiles = Files.list(MIGRATION_DIRECTORY)) {
            migrationsByVersion = migrationFiles
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> VERSIONED_SQL_MIGRATION.matcher(name).matches())
                    .collect(Collectors.groupingBy(
                            name -> VERSIONED_SQL_MIGRATION.matcher(name).replaceFirst("$1"),
                            Collectors.toList()
                    ));
        }

        Map<String, List<String>> duplicateVersions = migrationsByVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertTrue(duplicateVersions.isEmpty(), () -> "Duplicate Flyway migration versions: " + duplicateVersions);
    }
}
