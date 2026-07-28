package com.cacanode.api.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

class StoragePrefixOwnershipTest {
    @Test
    void recruitmentStoragePrefixIsOwnedByRecruitment() throws Exception {
        Path root = Path.of("src/main/java/com/cacanode/api");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (!relative.startsWith("recruitment/")
                        && Files.readString(file).contains("\"recruitment/")) {
                    fail(relative + " uses the recruitment-owned SeaweedFS prefix");
                }
            }
        }
    }
}
