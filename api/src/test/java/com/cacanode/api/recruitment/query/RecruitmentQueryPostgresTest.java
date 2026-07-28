package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.recruitment.model.RecruitmentEnums.JobStatus;
import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentQueryPostgresTest {
    private static final UUID FIRST=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static String jdbcUrl;
    private static UUID tenant;
    private static RecruitmentQueryService queries;

    @BeforeAll
    static void setUp()throws SQLException{
        jdbcUrl=PostgresTestContainer.createDatabase("phase3_queries");
        Flyway.configure().dataSource(jdbcUrl,PostgresTestContainer.username(),PostgresTestContainer.password()).locations("classpath:db/migration").load().migrate();
        DriverManagerDataSource dataSource=new DriverManagerDataSource(jdbcUrl,PostgresTestContainer.username(),PostgresTestContainer.password());
        queries=new RecruitmentQueryService(new NamedParameterJdbcTemplate(dataSource));
        tenant=tenant("query-one");UUID other=tenant("query-two");
        job(tenant,FIRST,"Engineer","Engineering","Hanoi",LocalDateTime.of(2026,8,10,0,0));
        job(tenant,SECOND,"Engineer","Engineering","Remote",LocalDateTime.of(2026,8,20,0,0));
        job(tenant,UUID.fromString("00000000-0000-0000-0000-000000000003"),"Designer","Product","Hanoi",LocalDateTime.of(2026,9,1,0,0));
        job(other,UUID.fromString("00000000-0000-0000-0000-000000000004"),"Engineer","Engineering","Hanoi",LocalDateTime.of(2026,8,15,0,0));
    }

    @Test
    void filtersCountsPagesAndUsesIdAsTieBreaker(){
        var firstPage=queries.jobs(tenant,0,1,JobStatus.DRAFT,"Engineering",null,null,null,"en-US",
                LocalDateTime.of(2026,8,1,0,0),LocalDateTime.of(2026,9,1,0,0),"engineer","title","ASC");
        assertEquals(2,firstPage.totalCount());assertEquals(1,firstPage.items().size());assertEquals(FIRST,firstPage.items().getFirst().id());
        var secondPage=queries.jobs(tenant,1,1,JobStatus.DRAFT,"Engineering",null,null,null,"en-US",
                LocalDateTime.of(2026,8,1,0,0),LocalDateTime.of(2026,9,1,0,0),"engineer","title","ASC");
        assertEquals(SECOND,secondPage.items().getFirst().id());
    }

    @Test
    void rejectsUnknownSortDirectionAndPageBounds(){
        assertThrows(BadRequestException.class,()->queries.jobs(tenant,0,20,null,null,null,null,null,null,null,null,null,"unknown","ASC"));
        assertThrows(BadRequestException.class,()->queries.jobs(tenant,0,20,null,null,null,null,null,null,null,null,null,"createdAt","sideways"));
        assertThrows(BadRequestException.class,()->queries.jobs(tenant,0,101,null,null,null,null,null,null,null,null,null,null,null));
    }

    private static UUID tenant(String slug)throws SQLException{UUID id=UUID.randomUUID();try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO tenants(id,name,slug,plan,status,max_documents,max_messages,max_storage_mb,created_at,updated_at) VALUES (?,?,?,'PRO','ACTIVE',10,100,1000,NOW(),NOW())")){s.setObject(1,id);s.setString(2,slug);s.setString(3,slug);s.executeUpdate();}return id;}
    private static void job(UUID tenantId,UUID id,String title,String department,String location,LocalDateTime closing)throws SQLException{try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO recruitment_jobs(id,tenant_id,public_id,title,description,department,location,language,status,cv_policy,closing_at,created_at,updated_at) VALUES (?,?,gen_random_uuid(),?, 'Description',?,?, 'en-US','DRAFT','OPTIONAL',?,'2026-07-01 00:00:00','2026-07-01 00:00:00')")){s.setObject(1,id);s.setObject(2,tenantId);s.setString(3,title);s.setString(4,department);s.setString(5,location);s.setTimestamp(6,Timestamp.valueOf(closing));s.executeUpdate();}}
    private static Connection connection()throws SQLException{return DriverManager.getConnection(jdbcUrl,PostgresTestContainer.username(),PostgresTestContainer.password());}
}
