package com.cacanode.api.bootstrap.diagnostics;

import com.cacanode.api.document.messaging.RabbitMqTopology;
import com.cacanode.api.platform.api.PlatformDiagnosticsApi;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import com.cacanode.api.recruitment.config.RecruitmentRabbitTopology;
import com.cacanode.api.common.storage.SeaweedFsProperties;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static com.cacanode.api.platform.api.PlatformDiagnosticsApi.Component;
import static com.cacanode.api.platform.api.PlatformDiagnosticsApi.ComponentResult;
import static com.cacanode.api.platform.api.PlatformDiagnosticsApi.ErrorCode;
import static com.cacanode.api.platform.api.PlatformDiagnosticsApi.QueueDomain;
import static com.cacanode.api.platform.api.PlatformDiagnosticsApi.QueueId;
import static com.cacanode.api.platform.api.PlatformDiagnosticsApi.QueueResult;
import static com.cacanode.api.platform.api.PlatformDiagnosticsApi.Status;

@org.springframework.stereotype.Component
@ConditionalOnProperty(prefix = "app.platform-administration", name = "enabled", havingValue = "true")
public class PlatformDiagnosticsAdapter implements PlatformDiagnosticsApi, AutoCloseable {
    private static final List<QueueDefinition> QUEUES = List.of(
            new QueueDefinition(QueueId.DOCUMENT_INGESTION, QueueDomain.DOCUMENT, false, RabbitMqTopology.INGESTION_QUEUE, QueueFeature.ALWAYS),
            new QueueDefinition(QueueId.DOCUMENT_STATUS, QueueDomain.DOCUMENT, false, RabbitMqTopology.STATUS_QUEUE, QueueFeature.ALWAYS),
            new QueueDefinition(QueueId.DOCUMENT_INGESTION_DLQ, QueueDomain.DOCUMENT, true, RabbitMqTopology.INGESTION_DLQ, QueueFeature.ALWAYS),
            new QueueDefinition(QueueId.DOCUMENT_STATUS_DLQ, QueueDomain.DOCUMENT, true, RabbitMqTopology.STATUS_DLQ, QueueFeature.ALWAYS),
            new QueueDefinition(QueueId.RECRUITMENT_RESUME_ANALYSIS, QueueDomain.RECRUITMENT, false, RecruitmentRabbitTopology.RESUME_ANALYSIS_QUEUE, QueueFeature.CV_AI),
            new QueueDefinition(QueueId.RECRUITMENT_INTERVIEW_EVENTS, QueueDomain.RECRUITMENT, false, RecruitmentRabbitTopology.INTERVIEW_EVENTS_QUEUE, QueueFeature.RECRUITMENT),
            new QueueDefinition(QueueId.RECRUITMENT_RECORDING_OPERATIONS, QueueDomain.RECRUITMENT, false, RecruitmentRabbitTopology.RECORDING_OPERATION_QUEUE, QueueFeature.RECORDING),
            new QueueDefinition(QueueId.RECRUITMENT_RESUME_ANALYSIS_DLQ, QueueDomain.RECRUITMENT, true, RecruitmentRabbitTopology.RESUME_ANALYSIS_DLQ, QueueFeature.CV_AI),
            new QueueDefinition(QueueId.RECRUITMENT_INTERVIEW_EVENTS_DLQ, QueueDomain.RECRUITMENT, true, RecruitmentRabbitTopology.INTERVIEW_EVENTS_DLQ, QueueFeature.RECRUITMENT),
            new QueueDefinition(QueueId.RECRUITMENT_RECORDING_OPERATIONS_DLQ, QueueDomain.RECRUITMENT, true, RecruitmentRabbitTopology.RECORDING_OPERATION_DLQ, QueueFeature.RECORDING));

    private final PlatformDiagnosticsProperties properties;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final DataSource dataSource;
    private final RedisConnectionFactory redis;
    private final ConnectionFactory rabbit;
    private final S3Client s3;
    private final SeaweedFsProperties seaweed;
    private final RecruitmentProperties recruitment;
    private final PublicRecruitmentProperties publicRecruitment;
    private final HttpClient http;
    private final ExecutorService executor;
    private final Object refreshLock = new Object();
    private volatile CachedSnapshot cached;
    private CompletableFuture<Snapshot> inFlight;

    @Autowired
    public PlatformDiagnosticsAdapter(
            PlatformDiagnosticsProperties properties,
            Clock clock,
            DataSource dataSource,
            RedisConnectionFactory redis,
            ConnectionFactory rabbit,
            S3Client s3,
            SeaweedFsProperties seaweed,
            RecruitmentProperties recruitment,
            PublicRecruitmentProperties publicRecruitment) {
        this(properties, clock, System::nanoTime, dataSource, redis, rabbit, s3, seaweed, recruitment,
                publicRecruitment, null, null);
    }

    PlatformDiagnosticsAdapter(
            PlatformDiagnosticsProperties properties,
            Clock clock,
            LongSupplier nanoTime,
            DataSource dataSource,
            RedisConnectionFactory redis,
            ConnectionFactory rabbit,
            S3Client s3,
            SeaweedFsProperties seaweed,
            RecruitmentProperties recruitment,
            PublicRecruitmentProperties publicRecruitment,
            HttpClient http,
            ExecutorService executor) {
        this.properties = properties;
        this.clock = clock;
        this.nanoTime = nanoTime;
        this.dataSource = dataSource;
        this.redis = redis;
        this.rabbit = rabbit;
        this.s3 = s3;
        this.seaweed = seaweed;
        this.recruitment = recruitment;
        this.publicRecruitment = publicRecruitment;
        this.http = http == null ? HttpClient.newBuilder()
                .connectTimeout(properties.probeTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build() : http;
        this.executor = executor == null ? Executors.newFixedThreadPool(properties.parallelism(), daemonThreads()) : executor;
    }

    @Override
    public HealthSnapshot health() {
        Snapshot snapshot = snapshot();
        return new HealthSnapshot(snapshot.time, overallComponents(snapshot.components),
                List.copyOf(snapshot.components.values()), runtimeMetrics());
    }

    @Override
    public QueuePage queues(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        Snapshot snapshot = snapshot();
        int from = (int) Math.min((long) page * size, snapshot.queues.size());
        int to = Math.min(from + size, snapshot.queues.size());
        return new QueuePage(List.copyOf(snapshot.queues.subList(from, to)), page, size, snapshot.queues.size(),
                snapshot.time, overallQueues(snapshot.queues), properties.warningDepth(), properties.criticalDepth());
    }

    private Snapshot snapshot() {
        long now = nanoTime.getAsLong();
        CachedSnapshot current = cached;
        if (current != null && now - current.createdNanos < properties.cacheTtl().toNanos()) return current.snapshot;

        CompletableFuture<Snapshot> future;
        boolean creator = false;
        synchronized (refreshLock) {
            now = nanoTime.getAsLong();
            current = cached;
            if (current != null && now - current.createdNanos < properties.cacheTtl().toNanos()) return current.snapshot;
            if (inFlight == null) {
                inFlight = new CompletableFuture<>();
                creator = true;
            }
            future = inFlight;
        }
        if (creator) {
            try {
                Snapshot refreshed = refresh();
                cached = new CachedSnapshot(refreshed, nanoTime.getAsLong());
                future.complete(refreshed);
            } catch (Throwable failure) {
                Snapshot safe = failedSnapshot();
                cached = new CachedSnapshot(safe, nanoTime.getAsLong());
                future.complete(safe);
            } finally {
                synchronized (refreshLock) {
                    if (inFlight == future) inFlight = null;
                }
            }
        }
        return future.join();
    }

    private Snapshot refresh() {
        long started = nanoTime.getAsLong();
        long probeDeadline = started + properties.probeTimeout().toNanos();
        long totalDeadline = started + properties.refreshBudget().toNanos();
        Instant snapshotTime = clock.instant();
        Map<Component, Future<ComponentResult>> componentFutures = new EnumMap<>(Component.class);
        componentFutures.put(Component.POSTGRESQL, submit(Component.POSTGRESQL, this::postgres));
        componentFutures.put(Component.REDIS, submit(Component.REDIS, this::redis));
        componentFutures.put(Component.AI_API, submit(Component.AI_API, () -> http(properties.aiApiUrl(), false, null)));
        componentFutures.put(Component.GRAPH_SERVICE, submit(Component.GRAPH_SERVICE, () -> http(properties.graphServiceUrl(), false, null)));
        componentFutures.put(Component.QDRANT, submit(Component.QDRANT, () -> http(properties.qdrantUrl(), false,
                hasText(properties.qdrantApiKey()) ? Map.entry("api-key", properties.qdrantApiKey()) : null)));
        componentFutures.put(Component.OLLAMA, submit(Component.OLLAMA, () -> http(properties.ollamaUrl(), false, null)));
        componentFutures.put(Component.RERANKER, submit(Component.RERANKER, this::reranker));
        componentFutures.put(Component.SEAWEEDFS, submit(Component.SEAWEEDFS, this::seaweed));
        componentFutures.put(Component.CLAMAV, submit(Component.CLAMAV, this::clamav));
        componentFutures.put(Component.PUBLIC_EDGE, submit(Component.PUBLIC_EDGE, this::publicEdge));
        Future<RabbitResult> rabbitFuture = executor.submit(this::rabbit);

        EnumMap<Component, ComponentResult> components = new EnumMap<>(Component.class);
        components.put(Component.BUSINESS_API_JVM, new ComponentResult(Component.BUSINESS_API_JVM, Status.UP, 0L, clock.instant(), null));
        for (Map.Entry<Component, Future<ComponentResult>> entry : componentFutures.entrySet()) {
            components.put(entry.getKey(), await(entry.getKey(), entry.getValue(), probeDeadline, totalDeadline));
        }
        RabbitResult rabbitResult = awaitRabbit(rabbitFuture, probeDeadline, totalDeadline);
        components.put(Component.RABBITMQ, rabbitResult.component);
        List<QueueResult> queues = queueResults(rabbitResult.queues, rabbitResult.component.checkedAt());
        components.put(Component.DOCUMENT_WORKER, documentWorker(queues));
        return new Snapshot(snapshotTime, components, queues);
    }

    private Future<ComponentResult> submit(Component component, CheckedProbe probe) {
        return executor.submit(() -> measured(component, probe));
    }

    private ComponentResult measured(Component component, CheckedProbe probe) {
        long start = nanoTime.getAsLong();
        try {
            ProbeResult result = probe.run();
            return new ComponentResult(component, result.status, millis(start), clock.instant(), result.errorCode);
        } catch (Throwable failure) {
            Mapping mapping = mapFailure(failure);
            return new ComponentResult(component, mapping.status, millis(start), clock.instant(), mapping.errorCode);
        }
    }

    private ComponentResult await(Component component, Future<ComponentResult> future, long probeDeadline, long totalDeadline) {
        long remaining = Math.min(probeDeadline, totalDeadline) - nanoTime.getAsLong();
        if (remaining <= 0 && !future.isDone()) {
            future.cancel(true);
            return timeout(component);
        }
        try {
            return remaining <= 0 ? future.get() : future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException failure) {
            future.cancel(true);
            return timeout(component);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return timeout(component);
        } catch (ExecutionException failure) {
            Mapping mapping = mapFailure(failure.getCause());
            return new ComponentResult(component, mapping.status, null, clock.instant(), mapping.errorCode);
        }
    }

    private RabbitResult awaitRabbit(Future<RabbitResult> future, long probeDeadline, long totalDeadline) {
        long remaining = Math.min(probeDeadline, totalDeadline) - nanoTime.getAsLong();
        try {
            if (remaining <= 0 && !future.isDone()) throw new TimeoutException();
            return remaining <= 0 ? future.get() : future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException failure) {
            future.cancel(true);
            return rabbitFailure(Status.DOWN, ErrorCode.TIMEOUT);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return rabbitFailure(Status.DOWN, ErrorCode.TIMEOUT);
        } catch (ExecutionException failure) {
            Mapping mapping = mapFailure(failure.getCause());
            return rabbitFailure(mapping.status, mapping.errorCode);
        }
    }

    private ProbeResult postgres() throws Exception {
        int seconds = Math.max(1, (int) Math.ceil(properties.probeTimeout().toMillis() / 1000.0));
        try (java.sql.Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(seconds);
            try (var result = statement.executeQuery("SELECT 1")) {
                return result.next() && result.getInt(1) == 1 ? up() : unexpected();
            }
        }
    }

    private ProbeResult redis() {
        try (var connection = redis.getConnection()) {
            String pong = connection.ping();
            return "PONG".equalsIgnoreCase(pong) ? up() : unexpected();
        }
    }

    private ProbeResult reranker() throws Exception {
        return properties.rerankerEnabled() ? http(properties.rerankerUrl(), false, null) : disabled();
    }

    private ProbeResult publicEdge() throws Exception {
        return hasText(properties.publicEdgeUrl()) ? http(properties.publicEdgeUrl(), true, null) : disabled();
    }

    private ProbeResult http(String url, boolean acceptRedirect, Map.Entry<String, String> header) throws Exception {
        if (!hasText(url)) return unknown();
        URI uri;
        try {
            uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) return unknown();
        } catch (IllegalArgumentException malformed) {
            return unknown();
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET().timeout(properties.probeTimeout());
        if (header != null) request.header(header.getKey(), header.getValue());
        int status = http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        return mapHttpStatus(status, acceptRedirect);
    }

    static ProbeResult mapHttpStatus(int status, boolean acceptRedirect) {
        if (status >= 200 && status < 300 || acceptRedirect && status >= 300 && status < 400) return up();
        if (status == 503) return degraded(ErrorCode.NOT_READY_RESPONSE);
        if (status == 401 || status == 403) return down(ErrorCode.AUTHENTICATION_FAILURE);
        if (status >= 500) return down(ErrorCode.NOT_READY_RESPONSE);
        return down(ErrorCode.UNEXPECTED_RESPONSE);
    }

    private ProbeResult seaweed() {
        if (!hasText(seaweed.bucket())) return unknown();
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(seaweed.bucket())
                    .overrideConfiguration(builder -> builder.apiCallTimeout(properties.probeTimeout()))
                    .build());
            return up();
        } catch (SdkServiceException failure) {
            if (failure.statusCode() == 404) return new ProbeResult(Status.DOWN, ErrorCode.STORAGE_BUCKET_MISSING);
            if (failure.statusCode() == 401 || failure.statusCode() == 403) return down(ErrorCode.AUTHENTICATION_FAILURE);
            throw failure;
        }
    }

    private ProbeResult clamav() throws IOException {
        if (!recruitment.enabled() || !publicRecruitment.scannerEnabled()) return disabled();
        try (Socket socket = new Socket()) {
            int timeout = Math.toIntExact(Math.min(Integer.MAX_VALUE, properties.probeTimeout().toMillis()));
            socket.connect(new InetSocketAddress(publicRecruitment.clamavHost(), publicRecruitment.clamavPort()), timeout);
            socket.setSoTimeout(timeout);
            socket.getOutputStream().write("zPING\0".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            byte[] response = socket.getInputStream().readNBytes(5);
            String value = new String(response, StandardCharsets.US_ASCII).replace("\0", "").trim();
            return "PONG".equals(value) ? up() : unexpected();
        }
    }

    private RabbitResult rabbit() {
        long start = nanoTime.getAsLong();
        EnumMap<QueueId, RawQueue> queues = new EnumMap<>(QueueId.class);
        try (Connection connection = rabbit.createConnection()) {
            for (QueueDefinition definition : QUEUES) {
                if (!enabled(definition.feature)) continue;
                try (Channel channel = connection.createChannel(false)) {
                    AMQP.Queue.DeclareOk declared = channel.queueDeclarePassive(definition.brokerName);
                    queues.put(definition.id, new RawQueue(declared.getMessageCount(), declared.getConsumerCount(), null));
                } catch (Throwable failure) {
                    ErrorCode code = isMissingQueue(failure) ? ErrorCode.QUEUE_MISSING : ErrorCode.PROBE_FAILURE;
                    queues.put(definition.id, new RawQueue(0, 0, code));
                }
            }
            return new RabbitResult(new ComponentResult(Component.RABBITMQ, Status.UP, millis(start), clock.instant(), null), queues);
        } catch (Throwable failure) {
            Mapping mapping = mapFailure(failure);
            return rabbitFailure(mapping.status, mapping.errorCode);
        }
    }

    private List<QueueResult> queueResults(Map<QueueId, RawQueue> raw, Instant checkedAt) {
        List<QueueResult> results = new ArrayList<>(QUEUES.size());
        for (QueueDefinition definition : QUEUES) {
            if (!enabled(definition.feature)) {
                results.add(new QueueResult(definition.id, definition.domain, definition.dlq, 0, 0,
                        Status.DISABLED, checkedAt, null));
                continue;
            }
            RawQueue value = raw.get(definition.id);
            if (value == null) value = new RawQueue(0, 0, ErrorCode.PROBE_FAILURE);
            QueueDecision decision = deriveQueueStatus(definition.dlq, value.ready, value.consumers,
                    properties.warningDepth(), properties.criticalDepth(), value.errorCode);
            results.add(new QueueResult(definition.id, definition.domain, definition.dlq, value.ready,
                    value.consumers, decision.status, checkedAt, decision.errorCode));
        }
        return results;
    }

    private ComponentResult documentWorker(List<QueueResult> queues) {
        QueueResult ingestion = queues.stream().filter(value -> value.queueId() == QueueId.DOCUMENT_INGESTION).findFirst().orElseThrow();
        Status status;
        ErrorCode error;
        if (ingestion.status() == Status.UNKNOWN) { status = Status.UNKNOWN; error = ingestion.errorCode(); }
        else if (ingestion.consumerCount() > 0) { status = Status.UP; error = null; }
        else if (ingestion.readyCount() >= properties.criticalDepth()) { status = Status.DOWN; error = ErrorCode.QUEUE_CRITICAL_DEPTH; }
        else { status = Status.DEGRADED; error = ErrorCode.CONSUMERS_ABSENT; }
        return new ComponentResult(Component.DOCUMENT_WORKER, status, null, ingestion.checkedAt(), error);
    }

    static QueueDecision deriveQueueStatus(boolean deadLetterQueue, long readyCount, int consumerCount,
                                           long warningDepth, long criticalDepth, ErrorCode unreadableCode) {
        if (unreadableCode != null) return new QueueDecision(Status.UNKNOWN, unreadableCode);
        if (deadLetterQueue && readyCount > 0) return new QueueDecision(Status.DOWN, ErrorCode.DLQ_NOT_EMPTY);
        if (readyCount >= criticalDepth) return new QueueDecision(Status.DOWN, ErrorCode.QUEUE_CRITICAL_DEPTH);
        if (!deadLetterQueue && consumerCount == 0) return new QueueDecision(Status.DEGRADED, ErrorCode.CONSUMERS_ABSENT);
        if (readyCount >= warningDepth) return new QueueDecision(Status.DEGRADED, ErrorCode.QUEUE_WARNING_DEPTH);
        return new QueueDecision(Status.UP, null);
    }

    private boolean enabled(QueueFeature feature) {
        return switch (feature) {
            case ALWAYS -> true;
            case RECRUITMENT -> recruitment.enabled() && recruitment.messagingEnabled();
            case CV_AI -> recruitment.enabled() && recruitment.messagingEnabled() && recruitment.cvAiEnabled();
            case RECORDING -> recruitment.enabled() && recruitment.messagingEnabled() && recruitment.recordingEnabled();
        };
    }

    private RuntimeMetrics runtimeMetrics() {
        Runtime runtime = Runtime.getRuntime();
        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        Double cpu = null;
        if (operatingSystem instanceof com.sun.management.OperatingSystemMXBean extended) {
            double load = extended.getProcessCpuLoad();
            if (load >= 0) cpu = Math.min(100.0, load * 100.0);
        }
        File filesystem = new File(".");
        return new RuntimeMetrics(ResourceScope.APPLICATION_CONTAINER, CpuScope.JVM_PROCESS, cpu,
                runtime.availableProcessors(), memory.getUsed(), memory.getCommitted(), memory.getMax(),
                ManagementFactory.getRuntimeMXBean().getUptime(), filesystem.getTotalSpace(), filesystem.getUsableSpace());
    }

    private Status overallComponents(Map<Component, ComponentResult> components) {
        boolean degraded = false;
        for (ComponentResult result : components.values()) {
            if (result.status() == Status.DISABLED) continue;
            if (result.status() == Status.DOWN) return Status.DOWN;
            if (result.status() == Status.DEGRADED || result.status() == Status.UNKNOWN) degraded = true;
        }
        return degraded ? Status.DEGRADED : Status.UP;
    }

    private Status overallQueues(List<QueueResult> queues) {
        boolean degraded = false;
        for (QueueResult result : queues) {
            if (result.status() == Status.DISABLED) continue;
            if (result.status() == Status.DOWN) return Status.DOWN;
            if (result.status() == Status.DEGRADED || result.status() == Status.UNKNOWN) degraded = true;
        }
        return degraded ? Status.DEGRADED : Status.UP;
    }

    private Snapshot failedSnapshot() {
        Instant now = clock.instant();
        EnumMap<Component, ComponentResult> components = new EnumMap<>(Component.class);
        for (Component component : Component.values()) {
            Status status = component == Component.BUSINESS_API_JVM ? Status.UP : Status.UNKNOWN;
            components.put(component, new ComponentResult(component, status, component == Component.BUSINESS_API_JVM ? 0L : null,
                    now, status == Status.UP ? null : ErrorCode.PROBE_FAILURE));
        }
        List<QueueResult> queues = QUEUES.stream().map(definition -> new QueueResult(definition.id, definition.domain,
                definition.dlq, 0, 0, enabled(definition.feature) ? Status.UNKNOWN : Status.DISABLED, now,
                enabled(definition.feature) ? ErrorCode.PROBE_FAILURE : null)).toList();
        return new Snapshot(now, components, queues);
    }

    private RabbitResult rabbitFailure(Status status, ErrorCode code) {
        return new RabbitResult(new ComponentResult(Component.RABBITMQ, status, null, clock.instant(), code), Map.of());
    }

    private ComponentResult timeout(Component component) {
        return new ComponentResult(component, Status.DOWN, null, clock.instant(), ErrorCode.TIMEOUT);
    }

    private Mapping mapFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) return new Mapping(Status.DOWN, ErrorCode.TIMEOUT);
            if (current instanceof ConnectException) return new Mapping(Status.DOWN, ErrorCode.CONNECTION_FAILURE);
            if (current instanceof SocketException || current instanceof SQLException)
                return new Mapping(Status.DOWN, ErrorCode.CONNECTION_FAILURE);
            String simpleName = current.getClass().getSimpleName();
            if (simpleName.contains("Authentication") || simpleName.contains("AuthFailure"))
                return new Mapping(Status.DOWN, ErrorCode.AUTHENTICATION_FAILURE);
            if (simpleName.contains("ConnectionFailure") || simpleName.contains("ConnectException"))
                return new Mapping(Status.DOWN, ErrorCode.CONNECTION_FAILURE);
            if (current instanceof SdkServiceException service) {
                if (service.statusCode() == 401 || service.statusCode() == 403)
                    return new Mapping(Status.DOWN, ErrorCode.AUTHENTICATION_FAILURE);
            }
            current = current.getCause();
        }
        return new Mapping(Status.DOWN, ErrorCode.PROBE_FAILURE);
    }

    private boolean isMissingQueue(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof com.rabbitmq.client.ShutdownSignalException shutdown
                    && shutdown.getReason() instanceof AMQP.Channel.Close close && close.getReplyCode() == 404) return true;
            current = current.getCause();
        }
        return false;
    }

    private long millis(long start) { return Math.max(0, TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - start)); }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static ProbeResult up() { return new ProbeResult(Status.UP, null); }
    private static ProbeResult disabled() { return new ProbeResult(Status.DISABLED, null); }
    private static ProbeResult unknown() { return new ProbeResult(Status.UNKNOWN, ErrorCode.PROBE_FAILURE); }
    private static ProbeResult unexpected() { return new ProbeResult(Status.DOWN, ErrorCode.UNEXPECTED_RESPONSE); }
    private static ProbeResult down(ErrorCode code) { return new ProbeResult(Status.DOWN, code); }
    private static ProbeResult degraded(ErrorCode code) { return new ProbeResult(Status.DEGRADED, code); }

    private static ThreadFactory daemonThreads() {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "platform-diagnostics-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override public void close() { executor.shutdownNow(); }

    @FunctionalInterface private interface CheckedProbe { ProbeResult run() throws Exception; }
    private enum QueueFeature { ALWAYS, RECRUITMENT, CV_AI, RECORDING }
    private record QueueDefinition(QueueId id, QueueDomain domain, boolean dlq, String brokerName, QueueFeature feature) {}
    record ProbeResult(Status status, ErrorCode errorCode) {}
    private record Mapping(Status status, ErrorCode errorCode) {}
    record QueueDecision(Status status, ErrorCode errorCode) {}
    private record RawQueue(long ready, int consumers, ErrorCode errorCode) {}
    private record RabbitResult(ComponentResult component, Map<QueueId, RawQueue> queues) {}
    private record Snapshot(Instant time, EnumMap<Component, ComponentResult> components, List<QueueResult> queues) {}
    private record CachedSnapshot(Snapshot snapshot, long createdNanos) {}
}
