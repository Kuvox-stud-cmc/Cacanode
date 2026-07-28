package com.cacanode.api.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

class PlatformControllerOwnershipTest {
    @Test
    void everyPlatformRouteIsOwnedByPlatform() throws Exception {
        Path root = Path.of("src/main/java/com/cacanode/api");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                String source = Files.readString(file);
                if (!relative.startsWith("platform/")
                        && source.contains("@RequestMapping")
                        && source.contains("/api/v1/platform/")) {
                    fail(relative + " declares a platform-owned HTTP route");
                }
            }
        }
    }

    @Test
    void platformJobControllerUsesOnlyRecruitmentPublishedApi() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/cacanode/api/platform/controller/PlatformJobController.java"));
        if (!source.contains("com.cacanode.api.recruitment.api.RecruitmentPlatformReadApi"))
            fail("Platform job controller must use the recruitment-owned read API");
        if (source.contains("com.cacanode.api.recruitment.model") || source.contains("com.cacanode.api.recruitment.repository")
                || source.contains("com.cacanode.api.recruitment.query") || source.contains("com.cacanode.api.recruitment.dto"))
            fail("Platform job controller imports recruitment internals");
    }

    @Test
    void platformDiagnosticsControllerUsesOnlyPlatformPublishedApi() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/cacanode/api/platform/controller/PlatformDiagnosticsController.java"));
        if (!source.contains("com.cacanode.api.platform.api.PlatformDiagnosticsApi"))
            fail("Platform diagnostics controller must use the platform-owned API contract");
        if (source.contains("com.cacanode.api.bootstrap") || source.contains("org.springframework.jdbc")
                || source.contains("org.springframework.data.redis") || source.contains("org.springframework.amqp")
                || source.contains("software.amazon.awssdk"))
            fail("Platform diagnostics controller imports probing infrastructure");
    }

    @Test
    void platformFailureControllerUsesOnlyPlatformPublishedApi() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/cacanode/api/platform/controller/PlatformFailureController.java"));
        if (!source.contains("com.cacanode.api.platform.api.PlatformFailureApi"))
            fail("Platform failure controller must use the platform-owned API contract");
        if (source.contains("com.cacanode.api.common") || source.contains("com.cacanode.api.platform.service"))
            fail("Platform failure controller imports non-contract implementation types");
    }
}
