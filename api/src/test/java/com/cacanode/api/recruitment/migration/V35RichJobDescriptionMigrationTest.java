package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class V35RichJobDescriptionMigrationTest {
    private static String url;

    @BeforeAll static void migrate() {
        url = PostgresTestContainer.createDatabase("rich_job_descriptions");
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void addsNullableColumnsAndKeepsLegacyPlainRowsValid() throws Exception {
        try (Connection connection = DriverManager.getConnection(url,PostgresTestContainer.username(),PostgresTestContainer.password());
             Statement statement = connection.createStatement()) {
            assertColumnNullable(statement,"recruitment_jobs","description_html");
            assertColumnNullable(statement,"recruitment_public_jobs","description_html");
            UUID tenant = UUID.randomUUID();
            statement.executeUpdate("INSERT INTO tenants(id,name,slug) VALUES ('"+tenant+"','Legacy','legacy-"+tenant+"')");
            statement.executeUpdate("INSERT INTO recruitment_jobs(tenant_id,title,description,language,status,cv_policy) VALUES ('"+tenant+"','Legacy role','Plain only','en-US','DRAFT','OPTIONAL')");
            ResultSet row = statement.executeQuery("SELECT description,description_html FROM recruitment_jobs WHERE tenant_id='"+tenant+"'");
            assertTrue(row.next());
            assertEquals("Plain only",row.getString(1));
            assertNull(row.getString(2));
        }
    }

    private static void assertColumnNullable(Statement statement,String table,String column) throws Exception {
        ResultSet result = statement.executeQuery("SELECT is_nullable FROM information_schema.columns WHERE table_name='"+table+"' AND column_name='"+column+"'");
        assertTrue(result.next());
        assertEquals("YES",result.getString(1));
    }
}
