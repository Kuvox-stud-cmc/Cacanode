package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.recruitment.api.RecruitmentPlatformReadApi;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.cacanode.api.recruitment.api.RecruitmentPlatformReadApi.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecruitmentPlatformReadServicePostgresTest {
    private static final Instant NOW = Instant.parse("2026-07-28T06:00:00Z");
    private static final UUID TENANT = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID VISIBLE_JOB = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID DRAFT_JOB = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID EXPIRED_JOB = UUID.fromString("20000000-0000-4000-8000-000000000003");
    private static final String SNAPSHOT = """
            {"introductionText":"i","disclosureText":"d","closingText":"c","durationLimitSeconds":60,
             "interactionLimits":{"repetitionLimit":1,"clarificationLimit":1,"silenceTimeoutSeconds":10,"silencePromptLimit":1},
             "sections":[{}]}
            """;
    private static String url;
    private static RecruitmentPlatformReadService service;

    @BeforeAll
    static void setUp() throws Exception {
        url = PostgresTestContainer.createDatabase("platform_job_read");
        Flyway.configure().dataSource(url, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, PostgresTestContainer.username(), PostgresTestContainer.password());
        service = new RecruitmentPlatformReadService(new NamedParameterJdbcTemplate(dataSource),
                new RecruitmentProperties(true, false, false, true, false, false, false, false),
                Clock.fixed(NOW, ZoneOffset.UTC));
        seed();
    }

    @Test
    void filtersSafeMetadataAndUsesHalfOpenUtcRanges() {
        JobPage page = service.jobs(query(TENANT, JobStatus.PUBLISHED, "Acme", "en-US", "Engineering", "Hanoi",
                EmploymentType.FULL_TIME, WorkMode.HYBRID, Visibility.VISIBLE,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-07-28T05:00:00Z"), Instant.parse("2026-07-28T06:00:00Z"), Sort.UPDATED_AT, Direction.DESC));
        assertThat(page.total()).isOne();
        assertThat(page.items().getFirst().jobId()).isEqualTo(VISIBLE_JOB);

        assertThat(service.jobs(query(null, null, "secret description", null, null, null, null, null, null,
                null, null, null, null, Sort.UPDATED_AT, Direction.DESC)).total()).isZero();
        assertThat(service.jobs(query(null, null, VISIBLE_JOB.toString(), null, null, null, null, null, null,
                null, null, null, null, Sort.UPDATED_AT, Direction.DESC)).total()).isOne();
    }

    @Test
    void returnsAllLifecycleStatesButVisibilityReflectsPublicEligibility() {
        JobPage all = service.jobs(query(null, null, null, null, null, null, null, null, null,
                null, null, null, null, Sort.TITLE, Direction.ASC));
        assertThat(all.items()).extracting(JobItem::jobId).containsExactly(DRAFT_JOB, EXPIRED_JOB, VISIBLE_JOB);
        assertThat(all.items()).filteredOn(JobItem::visibleOnPublicBoard).extracting(JobItem::jobId)
                .containsExactly(VISIBLE_JOB);
        assertThat(all.items()).filteredOn(item -> !item.visibleOnPublicBoard()).hasSize(2);
        assertThat(all.items()).filteredOn(item -> item.jobId().equals(DRAFT_JOB)).first()
                .extracting(JobItem::frozenCompanyName).isNull();

        assertThat(publicBoardJobIds()).containsExactlyElementsOf(all.items().stream()
                .filter(JobItem::visibleOnPublicBoard).map(JobItem::jobId).toList());
    }

    @Test
    void pagesStablyPlacesNullTimestampsLastAndReturnsSafeAggregates() {
        JobPage first = service.jobs(new JobQuery(0, 2, null, null, null, null, null, null, null, null, null,
                null, null, null, null, Sort.PUBLISHED_AT, Direction.ASC));
        assertThat(first.total()).isEqualTo(3);
        assertThat(first.items()).extracting(JobItem::jobId).containsExactly(EXPIRED_JOB, VISIBLE_JOB);
        JobPage second = service.jobs(new JobQuery(1, 2, null, null, null, null, null, null, null, null, null,
                null, null, null, null, Sort.PUBLISHED_AT, Direction.ASC));
        assertThat(second.items()).extracting(JobItem::jobId).containsExactly(DRAFT_JOB);

        JobDetail detail = service.job(VISIBLE_JOB);
        assertThat(detail.totalApplications()).isEqualTo(2);
        assertThat(detail.verifiedApplications()).isOne();
        assertThat(detail.totalInterviews()).isEqualTo(2);
        assertThat(detail.completedInterviews()).isOne();
        assertThat(detail.unsuccessfulInterviews()).isOne();
        assertThrows(ResourceNotFoundException.class, () -> service.job(UUID.randomUUID()));
    }

    private static JobQuery query(UUID tenantId, JobStatus status, String search, String language, String department,
                                  String location, EmploymentType employmentType, WorkMode workMode, Visibility visibility,
                                  Instant closingFrom, Instant closingTo, Instant updatedFrom, Instant updatedTo,
                                  Sort sort, Direction direction) {
        return new JobQuery(0, 20, tenantId, status, search, language, department, location, employmentType, workMode,
                visibility, closingFrom, closingTo, updatedFrom, updatedTo, sort, direction);
    }

    private static void seed() throws Exception {
        try (Connection connection = connection(); Statement control = connection.createStatement()) {
            control.execute("SET session_replication_role=replica");
            insertJob(connection, VISIBLE_JOB, "Zulu Engineer", "PUBLISHED", "Acme Frozen", "Engineering", "Hanoi",
                    "FULL_TIME", "HYBRID", "en-US", "2026-07-20 00:00:00", "2026-08-20 00:00:00", "2026-07-28 05:59:59", "secret description");
            insertJob(connection, DRAFT_JOB, "Alpha Draft", "DRAFT", null, "Product", "Remote",
                    null, "REMOTE", "vi-VN", null, null, "2026-07-28 05:59:59", "private draft prose");
            insertJob(connection, EXPIRED_JOB, "Middle Expired", "PUBLISHED", "Old Company", "Sales", "Da Nang",
                    "CONTRACT", "ONSITE", "en-US", "2026-06-01 00:00:00", "2026-07-01 00:00:00", "2026-07-20 00:00:00", "expired prose");
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO recruitment_public_jobs(job_id,tenant_id,public_id,tenant_slug,company_name,title,
                        description,language,cv_policy,published_at,closing_at,discoverable)
                    SELECT id,tenant_id,public_id,'acme','Acme Frozen',title,'public prose',language,cv_policy,published_at,closing_at,true
                    FROM recruitment_jobs WHERE id IN (?,?)
                    """)) {
                statement.setObject(1, VISIBLE_JOB); statement.setObject(2, EXPIRED_JOB); statement.executeUpdate();
            }
            seedApplication(connection, VISIBLE_JOB, true, "COMPLETED");
            seedApplication(connection, VISIBLE_JOB, false, "FAILED");
            control.execute("SET session_replication_role=origin");
        }
    }

    private static void insertJob(Connection connection, UUID id, String title, String status, String company,
                                  String department, String location, String employment, String workMode, String language,
                                  String publishedAt, String closingAt, String updatedAt, String description) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO recruitment_jobs(id,tenant_id,public_id,title,description,department,location,employment_type,
                    work_mode,experience_level,language,status,cv_policy,effective_automation_mode,effective_cv_ai_mode,
                    template_revision_id,active_job_reservation_id,frozen_company_name,frozen_company_slug,published_at,closing_at,updated_at)
                VALUES (?,?,gen_random_uuid(),?,?,?,?,?,?,'MID',?,?,'OPTIONAL',?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setObject(1,id);statement.setObject(2,TENANT);statement.setString(3,title);statement.setString(4,description);
            statement.setString(5,department);statement.setString(6,location);statement.setString(7,employment);
            statement.setString(8,workMode);statement.setString(9,language);statement.setString(10,status);
            boolean published=publishedAt!=null;
            statement.setString(11,published?"MANUAL":null);statement.setString(12,published?"OFF":null);
            statement.setObject(13,published?UUID.randomUUID():null);statement.setObject(14,published?UUID.randomUUID():null);
            statement.setString(15,company);statement.setString(16,published?"company":null);
            timestamp(statement,17,publishedAt);timestamp(statement,18,closingAt);timestamp(statement,19,updatedAt);
            statement.executeUpdate();
        }
    }

    private static void seedApplication(Connection connection, UUID jobId, boolean verified, String interviewStatus) throws Exception {
        UUID application = UUID.randomUUID(), interview = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO recruitment_applications(id,tenant_id,job_id,candidate_id,status,submitted_at,verified_at,
                    locale,privacy_consent_at,template_revision_id,template_snapshot,template_snapshot_sha256,template_snapshot_version)
                VALUES (?,?,?,gen_random_uuid(),?,NOW(),?,'en-US',NOW(),gen_random_uuid(),?::jsonb,?,'1')
                """)) {
            statement.setObject(1,application);statement.setObject(2,TENANT);statement.setObject(3,jobId);
            statement.setString(4,verified?"SUBMITTED":"SUBMITTED_UNVERIFIED");timestamp(statement,5,verified?"2026-07-28 05:00:00":null);
            statement.setString(6,SNAPSHOT);statement.setString(7,"a".repeat(64));statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO recruitment_interviews(id,tenant_id,application_id,job_id,status,template_revision_id,
                    template_snapshot,template_snapshot_sha256,template_snapshot_version)
                SELECT ?,tenant_id,id,job_id,?,template_revision_id,?::jsonb,template_snapshot_sha256,'1'
                FROM recruitment_applications WHERE id=?
                """)) {
            statement.setObject(1,interview);statement.setString(2,interviewStatus);statement.setString(3,SNAPSHOT);
            statement.setObject(4,application);statement.executeUpdate();
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(url, PostgresTestContainer.username(), PostgresTestContainer.password());
    }

    private static java.util.List<UUID> publicBoardJobIds() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, PostgresTestContainer.username(), PostgresTestContainer.password());
        return new NamedParameterJdbcTemplate(dataSource).query("""
                SELECT job_id FROM recruitment_public_jobs
                WHERE discoverable AND closing_at>:now ORDER BY job_id
                """, java.util.Map.of("now", java.time.LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)),
                (rs,rowNum)->rs.getObject(1,UUID.class));
    }

    private static void timestamp(PreparedStatement statement, int index, String value) throws Exception {
        if (value == null) statement.setNull(index, Types.TIMESTAMP);
        else statement.setTimestamp(index, Timestamp.valueOf(value));
    }
}
