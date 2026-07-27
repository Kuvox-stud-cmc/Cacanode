package com.cacanode.api.recruitment.query;

import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.service.PublicJobCursorCodec;
import com.cacanode.api.recruitment.service.ScreeningSupport;
import com.cacanode.api.testsupport.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PublicJobDiscoverabilityPostgresTest {
    private static final UUID LISTED = UUID.randomUUID();
    private static final UUID UNLISTED = UUID.randomUUID();
    private static PublicJobQueryService jobs;
    private static String url;

    @BeforeAll static void setUp() throws Exception {
        url=PostgresTestContainer.createDatabase("public_job_discoverability");
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
        var dataSource=new DriverManagerDataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password());
        Clock clock=Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"),ZoneOffset.UTC);
        ObjectMapper mapper=new ObjectMapper();
        var properties=new PublicRecruitmentProperties(null,null,null,false,false,null,null,false,null,0,0);
        jobs=new PublicJobQueryService(new NamedParameterJdbcTemplate(dataSource),
                new PublicJobCursorCodec(properties,mapper,clock),clock,new ScreeningSupport(mapper));
        UUID tenant=UUID.randomUUID();
        try(Connection connection=connection()) {
            try(PreparedStatement statement=connection.prepareStatement("INSERT INTO tenants(id,name,slug) VALUES (?,?,?)")) {
                statement.setObject(1,tenant);statement.setString(2,"Acme");statement.setString(3,"acme");statement.executeUpdate();
            }
            seed(connection,tenant,LISTED,"Listed role",true,"<h2>Listed</h2><p>Visible content</p>");
            seed(connection,tenant,UNLISTED,"Unlisted role",false,"<h2>Private rollout</h2><p>Direct links work</p>");
        }
    }

    @Test void listingsExcludeUnlistedButDirectDetailReturnsBothWithRichContentAndFlag() {
        var page=jobs.search(new PublicJobQueryService.Search(null,null,null,null,null,null,null,null,"newest",null,20));
        assertEquals(1,page.items().size());
        assertEquals(LISTED,page.items().getFirst().publicId());
        assertTrue(page.items().getFirst().discoverable());
        assertEquals("<h2>Listed</h2><p>Visible content</p>",page.items().getFirst().descriptionHtml());

        var unlisted=jobs.detail(UNLISTED);
        assertEquals("Unlisted role",unlisted.title());
        assertFalse(unlisted.discoverable());
        assertEquals("<h2>Private rollout</h2><p>Direct links work</p>",unlisted.descriptionHtml());
    }

    private static void seed(Connection connection,UUID tenant,UUID publicId,String title,boolean discoverable,String html) throws Exception {
        UUID jobId=UUID.randomUUID();
        try(PreparedStatement statement=connection.prepareStatement("INSERT INTO recruitment_jobs(id,tenant_id,public_id,title,description,description_html,language,status,cv_policy) VALUES (?,?,?,?,?,?,'en-US','DRAFT','OPTIONAL')")) {
            statement.setObject(1,jobId);statement.setObject(2,tenant);statement.setObject(3,publicId);statement.setString(4,title);
            statement.setString(5,title+" plain description");statement.setString(6,html);statement.executeUpdate();
        }
        try(PreparedStatement statement=connection.prepareStatement("""
                INSERT INTO recruitment_public_jobs(job_id,tenant_id,public_id,tenant_slug,company_name,title,description,
                    description_html,language,cv_policy,published_at,closing_at,discoverable)
                VALUES (?,?,?,'acme','Acme',?,?,?,'en-US','OPTIONAL','2026-07-20 00:00:00','2026-08-20 00:00:00',?)
                """)) {
            statement.setObject(1,jobId);statement.setObject(2,tenant);statement.setObject(3,publicId);statement.setString(4,title);
            statement.setString(5,title+" plain description");statement.setString(6,html);statement.setBoolean(7,discoverable);statement.executeUpdate();
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(url,PostgresTestContainer.username(),PostgresTestContainer.password());
    }
}
