package com.cacanode.api.platform.api;

import java.time.Instant;
import java.util.List;

public interface PlatformDiagnosticsApi {
    HealthSnapshot health();

    QueuePage queues(int page, int size);

    enum Status { UP, DEGRADED, DOWN, DISABLED, UNKNOWN }

    enum Component {
        BUSINESS_API_JVM,
        POSTGRESQL,
        REDIS,
        RABBITMQ,
        AI_API,
        GRAPH_SERVICE,
        QDRANT,
        OLLAMA,
        RERANKER,
        SEAWEEDFS,
        CLAMAV,
        DOCUMENT_WORKER,
        PUBLIC_EDGE
    }

    enum ErrorCode {
        TIMEOUT,
        CONNECTION_FAILURE,
        NOT_READY_RESPONSE,
        AUTHENTICATION_FAILURE,
        UNEXPECTED_RESPONSE,
        STORAGE_BUCKET_MISSING,
        QUEUE_MISSING,
        QUEUE_WARNING_DEPTH,
        QUEUE_CRITICAL_DEPTH,
        CONSUMERS_ABSENT,
        DLQ_NOT_EMPTY,
        PROBE_FAILURE
    }

    enum ResourceScope { APPLICATION_CONTAINER }

    enum CpuScope { JVM_PROCESS }

    enum QueueId {
        DOCUMENT_INGESTION,
        DOCUMENT_STATUS,
        DOCUMENT_INGESTION_DLQ,
        DOCUMENT_STATUS_DLQ,
        RECRUITMENT_RESUME_ANALYSIS,
        RECRUITMENT_INTERVIEW_EVENTS,
        RECRUITMENT_RECORDING_OPERATIONS,
        RECRUITMENT_RESUME_ANALYSIS_DLQ,
        RECRUITMENT_INTERVIEW_EVENTS_DLQ,
        RECRUITMENT_RECORDING_OPERATIONS_DLQ
    }

    enum QueueDomain { DOCUMENT, RECRUITMENT }

    record ComponentResult(
            Component component,
            Status status,
            Long latencyMilliseconds,
            Instant checkedAt,
            ErrorCode errorCode) {
    }

    record RuntimeMetrics(
            ResourceScope scope,
            CpuScope cpuScope,
            Double processCpuPercentage,
            int availableProcessors,
            long heapUsedBytes,
            long heapCommittedBytes,
            long heapMaxBytes,
            long jvmUptimeMilliseconds,
            long filesystemTotalBytes,
            long filesystemUsableBytes) {
    }

    record HealthSnapshot(
            Instant snapshotTime,
            Status overallStatus,
            List<ComponentResult> components,
            RuntimeMetrics runtimeMetrics) {
    }

    record QueueResult(
            QueueId queueId,
            QueueDomain domain,
            boolean deadLetterQueue,
            long readyCount,
            int consumerCount,
            Status status,
            Instant checkedAt,
            ErrorCode errorCode) {
    }

    record QueuePage(
            List<QueueResult> items,
            int page,
            int size,
            long total,
            Instant snapshotTime,
            Status overallStatus,
            long warningDepth,
            long criticalDepth) {
    }
}
