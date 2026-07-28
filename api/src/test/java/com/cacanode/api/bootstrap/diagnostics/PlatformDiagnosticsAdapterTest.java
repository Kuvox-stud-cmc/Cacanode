package com.cacanode.api.bootstrap.diagnostics;

import com.cacanode.api.common.storage.SeaweedFsProperties;
import com.cacanode.api.platform.api.PlatformDiagnosticsApi;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import software.amazon.awssdk.services.s3.S3Client;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformDiagnosticsAdapterTest {
    private PlatformDiagnosticsAdapter adapter;

    @AfterEach void close() { if (adapter != null) adapter.close(); }

    @Test
    void convertsCompleteDependencyFailureIntoSafeSnapshotsAndReusesCache() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("private database detail"));
        RedisConnectionFactory redis = mock(RedisConnectionFactory.class);
        when(redis.getConnection()).thenThrow(new IllegalStateException("private redis detail"));
        ConnectionFactory rabbit = mock(ConnectionFactory.class);
        when(rabbit.createConnection()).thenThrow(new IllegalStateException("private broker detail"));
        RecruitmentProperties recruitment = new RecruitmentProperties(false, false, false, false, false, false, false, false);
        PublicRecruitmentProperties publicRecruitment = new PublicRecruitmentProperties("token", "cursor", "http://localhost",
                false, false, "", "", false, "localhost", 3310, 1024);
        SeaweedFsProperties seaweed = new SeaweedFsProperties("http://localhost", "", "", "", "us-east-1");
        AtomicLong nanos = new AtomicLong();
        adapter = new PlatformDiagnosticsAdapter(properties(), Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC),
                nanos::get, dataSource, redis, rabbit, mock(S3Client.class), seaweed, recruitment, publicRecruitment,
                null, null);

        var first = adapter.health();
        var second = adapter.queues(0, 20);
        assertThat(first.components()).hasSize(PlatformDiagnosticsApi.Component.values().length);
        assertThat(first.overallStatus()).isEqualTo(PlatformDiagnosticsApi.Status.DOWN);
        assertThat(second.items()).hasSize(10);
        assertThat(second.items()).filteredOn(item -> item.domain() == PlatformDiagnosticsApi.QueueDomain.RECRUITMENT)
                .allMatch(item -> item.status() == PlatformDiagnosticsApi.Status.DISABLED);
        verify(dataSource).getConnection();
        verify(redis).getConnection();
        verify(rabbit).createConnection();

        nanos.addAndGet(Duration.ofSeconds(11).toNanos());
        adapter.health();
        verify(dataSource, org.mockito.Mockito.times(2)).getConnection();
    }

    @Test
    void enforcesTheTotalRefreshBudgetAndReturnsControlledTimeouts() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            Thread.sleep(1_000);
            throw new SQLException("late private detail");
        });
        RedisConnectionFactory redis = mock(RedisConnectionFactory.class);
        when(redis.getConnection()).thenThrow(new IllegalStateException());
        ConnectionFactory rabbit = mock(ConnectionFactory.class);
        when(rabbit.createConnection()).thenThrow(new IllegalStateException());
        adapter = adapter(new PlatformDiagnosticsProperties(Duration.ofSeconds(5), Duration.ofMillis(100),
                Duration.ofSeconds(10), 12, 25, 100, "", "", "", "", "", "", false, ""),
                dataSource, redis, rabbit, System::nanoTime);

        long started = System.nanoTime();
        var health = adapter.health();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        var postgres = health.components().stream()
                .filter(result -> result.component() == PlatformDiagnosticsApi.Component.POSTGRESQL).findFirst().orElseThrow();
        assertThat(elapsedMillis).isLessThan(700);
        assertThat(postgres.status()).isEqualTo(PlatformDiagnosticsApi.Status.DOWN);
        assertThat(postgres.errorCode()).isEqualTo(PlatformDiagnosticsApi.ErrorCode.TIMEOUT);
    }

    @Test
    void collapsesConcurrentRefreshesIntoOneProbeSet() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            throw new SQLException();
        });
        RedisConnectionFactory redis = mock(RedisConnectionFactory.class);
        when(redis.getConnection()).thenThrow(new IllegalStateException());
        ConnectionFactory rabbit = mock(ConnectionFactory.class);
        when(rabbit.createConnection()).thenThrow(new IllegalStateException());
        adapter = adapter(properties(), dataSource, redis, rabbit, System::nanoTime);
        var callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(adapter::health);
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            var second = callers.submit(adapter::health);
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            verify(dataSource).getConnection();
            verify(rabbit).createConnection();
        } finally {
            callers.shutdownNow();
        }
    }

    private PlatformDiagnosticsProperties properties() {
        return new PlatformDiagnosticsProperties(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(10),
                12, 25, 100, "", "", "", "", "", "", false, "");
    }

    private PlatformDiagnosticsAdapter adapter(PlatformDiagnosticsProperties properties, DataSource dataSource,
                                                RedisConnectionFactory redis, ConnectionFactory rabbit,
                                                java.util.function.LongSupplier nanos) {
        return new PlatformDiagnosticsAdapter(properties,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC), nanos, dataSource, redis, rabbit,
                mock(S3Client.class), new SeaweedFsProperties("http://localhost", "", "", "", "us-east-1"),
                new RecruitmentProperties(false, false, false, false, false, false, false, false),
                new PublicRecruitmentProperties("token", "cursor", "http://localhost", false, false, "", "",
                        false, "localhost", 3310, 1024), null, null);
    }
}
