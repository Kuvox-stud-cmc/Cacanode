package com.cacanode.api.platform;

import com.cacanode.api.recruitment.api.RecruitmentPlatformReadApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformJobSerializationTest {
    private static final Set<String> FORBIDDEN = Set.of("description", "descriptionHtml", "screeningConfig",
            "screeningQuestions", "prompt", "options", "candidateId", "applicationId", "cvId", "transcript",
            "evaluation", "provider", "recording", "templateRevisionId", "cvPolicy");

    @Test
    void safeJobContractContainsNoSensitiveFieldNamesRecursively() {
        var detail = new RecruitmentPlatformReadApi.JobDetail(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, "Engineer", RecruitmentPlatformReadApi.JobStatus.DRAFT, null, null, "en-US", null, null, null,
                null, null, Instant.parse("2026-07-28T00:00:00Z"), false, false, 0, 0, 0, 0, 0);
        JsonNode json = new ObjectMapper().findAndRegisterModules().valueToTree(detail);
        assertSafe(json);
    }

    private static void assertSafe(JsonNode node) {
        if (node.isObject()) node.fields().forEachRemaining(field -> {
            assertThat(FORBIDDEN).doesNotContain(field.getKey());
            assertSafe(field.getValue());
        });
        else if (node.isArray()) node.forEach(PlatformJobSerializationTest::assertSafe);
    }
}
