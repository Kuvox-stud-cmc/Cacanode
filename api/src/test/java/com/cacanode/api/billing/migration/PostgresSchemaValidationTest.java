package com.cacanode.api.billing.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.show-sql=false",
        "app.recruitment.enabled=true"
})
@ActiveProfiles("test")
class PostgresSchemaValidationTest {
    private static final String JDBC_URL = PostgresTestContainer.createDatabase("schema_validation");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", PostgresTestContainer::username);
        registry.add("spring.datasource.password", PostgresTestContainer::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void contextStartsAgainstV26WithRecruitmentEnabledAndHibernateValidation() {
    }
}
