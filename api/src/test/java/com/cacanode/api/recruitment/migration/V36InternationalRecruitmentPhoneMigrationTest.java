package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V36InternationalRecruitmentPhoneMigrationTest {
    private static String url;

    @BeforeAll static void migrate() {
        url=PostgresTestContainer.createDatabase("international_recruitment_phones");
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void preservesVietnameseRowsAndAcceptsInternationalE164Candidates() throws Exception {
        try(Connection connection=connection();Statement statement=connection.createStatement()) {
            UUID tenant=UUID.randomUUID();
            statement.executeUpdate("INSERT INTO tenants(id,name,slug) VALUES ('"+tenant+"','Phones','phones-"+tenant+"')");
            insertCandidate(statement,tenant,"vn@example.com","+84901234567");
            insertCandidate(statement,tenant,"us@example.com","+14155552671");
            ResultSet count=statement.executeQuery("SELECT count(*) FROM recruitment_candidates WHERE tenant_id='"+tenant+"'");
            assertTrue(count.next());
            assertEquals(2,count.getInt(1));
            assertThrows(SQLException.class,()->insertCandidate(statement,tenant,"invalid@example.com","0901234567"));
        }
    }

    @Test void updatesBothOwnedPhoneConstraints() throws Exception {
        try(Connection connection=connection();Statement statement=connection.createStatement();ResultSet result=statement.executeQuery("""
                SELECT conname,pg_get_constraintdef(oid) definition FROM pg_constraint
                WHERE conname IN ('ck_recruitment_candidate_phone','ck_recruitment_call_attempt_destination')
                ORDER BY conname
                """)) {
            int found=0;
            while(result.next()) {
                found++;
                String definition=result.getString("definition");
                assertTrue(definition.contains("[1-9]"));
                assertTrue(definition.contains("{7,14}"));
            }
            assertEquals(2,found);
        }
    }

    private static void insertCandidate(Statement statement,UUID tenant,String email,String phone) throws SQLException {
        statement.executeUpdate("INSERT INTO recruitment_candidates(tenant_id,full_name,normalized_name,email,normalized_email,phone) VALUES ('"
                +tenant+"','Candidate','candidate','"+email+"','"+email+"','"+phone+"')");
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(url,PostgresTestContainer.username(),PostgresTestContainer.password());
    }
}
