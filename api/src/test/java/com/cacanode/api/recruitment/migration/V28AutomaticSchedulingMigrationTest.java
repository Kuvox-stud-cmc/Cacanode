package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class V28AutomaticSchedulingMigrationTest {
    private static String url;
    private static UUID tenant;

    @BeforeAll static void migrate() throws Exception {
        url=PostgresTestContainer.createDatabase("phase5_migration");
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("27")).load().migrate();
        tenant=UUID.randomUUID();
        try(Connection c=connection();Statement s=c.createStatement()){
            s.execute("INSERT INTO tenants(id,name,slug,plan,status,max_documents,max_messages,max_storage_mb,created_at,updated_at) VALUES ('"+tenant+"','Phase 5','phase-5','PRO','ACTIVE',10,100,1000,NOW(),NOW())");
            s.execute("INSERT INTO recruitment_tenant_settings(tenant_id,default_automation_mode,cv_ai_mode) VALUES ('"+tenant+"','AUTOMATIC','OFF')");
        }
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void createsSchedulingTablesAndConvertsAutomationModes() throws Exception {
        assertTrue(names("SELECT tablename FROM pg_tables WHERE schemaname='public'").containsAll(Set.of(
                "recruitment_availability_windows","recruitment_availability_exceptions",
                "recruitment_interview_invitation_tokens","recruitment_candidate_email_deliveries")));
        assertTrue(names("SELECT conname FROM pg_constraint").containsAll(Set.of(
                "ex_recruitment_interview_active_schedule","ex_recruitment_availability_window_overlap",
                "fk_recruitment_invitation_token_delivery")));
        try(Connection c=connection();PreparedStatement p=c.prepareStatement("SELECT default_automation_mode,scheduling_timezone,reminder_offsets_minutes FROM recruitment_tenant_settings WHERE tenant_id=?")){
            p.setObject(1,tenant);try(ResultSet r=p.executeQuery()){assertTrue(r.next());assertEquals("AUTO_INVITE_ALL",r.getString(1));assertEquals("Asia/Ho_Chi_Minh",r.getString(2));assertArrayEquals(new Integer[]{1440,60},(Integer[])r.getArray(3).getArray());}}
    }

    @Test void rejectsOverlappingWeeklyAvailability() throws Exception {
        try(Connection c=connection();PreparedStatement p=c.prepareStatement("INSERT INTO recruitment_availability_windows(tenant_id,day_of_week,start_local,end_local) VALUES (?,1,?::time,?::time)")){
            p.setObject(1,tenant);p.setString(2,"09:00");p.setString(3,"12:00");p.executeUpdate();
            p.setObject(1,tenant);p.setString(2,"11:00");p.setString(3,"13:00");assertThrows(SQLException.class,p::executeUpdate);
        }
    }

    private static Set<String> names(String sql)throws Exception{Set<String> result=new HashSet<>();try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())result.add(r.getString(1));}return result;}
    private static Connection connection()throws SQLException{return DriverManager.getConnection(url,PostgresTestContainer.username(),PostgresTestContainer.password());}
}
