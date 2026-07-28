import { getApiBase } from "@/lib/auth-api"
import { readJsonOrThrow } from "@/lib/api-error"
import type { ApiRequest } from "@/lib/api-request"

export type Metric = { value: number; previousValue: number; percentageChange: number }
export type Freshness = { projections: Record<string, string | null> }
export type DailyTrend = { date: string; tenants: number; jobs: number; verifiedApplications: number; completedInterviews: number; unsuccessfulInterviews: number }
export interface PlatformOverview {
  generatedAt: string; days: number; periodStart: string; periodEnd: string
  activeUsers: Metric; documents: Metric; storageBytes: Metric; conversations: Metric; openTickets: Metric
  jobs: Metric; verifiedApplications: Metric; completedInterviews: Metric; unsuccessfulInterviews: Metric
  tenantStatuses: Record<string, number>; tenantPlans: Record<string, number>; trends: DailyTrend[]
  freshness: Freshness; partial: boolean; warnings: string[]
}
export interface TenantItem {
  tenantId: string; name: string; status: string; plan: string; createdAt: string; updatedAt: string
  activeUsers: number; documents: number; storageBytes: number; conversations: number; openTickets: number
  jobs: number; verifiedApplications: number; completedInterviews: number
}
export interface TenantPage { generatedAt: string; items: TenantItem[]; page: number; size: number; total: number; freshness: Freshness; partial: boolean; warnings: string[] }
export interface TenantAggregates { totalUsers: number; activeUsers: number; documents: number; storageBytes: number; userMessages: number; conversations: number; totalTickets: number; openTickets: number; jobs: number; totalApplications: number; verifiedApplications: number; totalInterviews: number; completedInterviews: number; unsuccessfulInterviews: number }
export interface Quota { used: number; reserved: number; limit: number | null; utilizationPercentage: number | null; unlimited: boolean; overLimit: boolean }
export interface BillingDetail { plan: string; status: string; periodStart: string; periodEnd: string; quotas: Record<string, Quota> }
export interface RecruitmentActivation { tenantId: string; rolloutStage: string; masterEnabled: boolean; automationEnabled: boolean; cvAiEnabled: boolean; callingEnabled: boolean; recordingEnabled: boolean; publicDiscoveryEnabled: boolean; version: number }
export type FailureSource = "MODULE_EVENTS" | "DOCUMENT_INGESTION" | "WEBHOOKS" | "BILLING" | "CV_ANALYSIS" | "INTERVIEW_TRANSPORT" | "CANDIDATE_EMAIL" | "RECORDING" | "PRIVACY_ERASURE"
export type FailureState = "RETRYING" | "FAILED" | "DEAD" | "REVIEW" | "STALLED"
export type FailureSeverity = "WARNING" | "ERROR" | "CRITICAL"
export type FailureResourceType = "MODULE_EVENT" | "DOCUMENT" | "WEBHOOK_EVENT" | "PAYMENT_ORDER" | "BILLING_WEBHOOK" | "JOB"
export interface Failure { source: FailureSource; failureId: string; tenantId: string | null; resourceId: string | null; resourceType: FailureResourceType; state: FailureState; severity: FailureSeverity; errorCode: string; attempts: number; firstSeenAt: string; lastSeenAt: string; nextRetryAt: string | null }
export interface Warning { code: string; source: FailureSource | null }
export interface TenantDetail { generatedAt: string; tenantId: string; name: string; status: string; plan: string; createdAt: string; updatedAt: string; aggregates: TenantAggregates; billing: BillingDetail | null; recruitment: RecruitmentActivation | null; recentFailures: Failure[]; freshness: Freshness; partial: boolean; warnings: Warning[] }
export interface FailureSourceSummary { source: FailureSource; total: number; states: Partial<Record<FailureState, number>>; severities: Partial<Record<FailureSeverity, number>> }
export interface FailureSummary { generatedAt: string; sources: FailureSourceSummary[]; partial: boolean; warnings: Warning[] }
export interface FailurePage { generatedAt: string; source: FailureSource; items: Failure[]; page: number; size: number; total: number; partial: boolean; warnings: Warning[] }
export type PlatformJobStatus = "DRAFT" | "PUBLISHED" | "PAUSED" | "CLOSED" | "ARCHIVED"
export type PlatformJobVisibility = "VISIBLE" | "HIDDEN"
export type PlatformEmploymentType = "FULL_TIME" | "PART_TIME" | "CONTRACT" | "TEMPORARY" | "INTERNSHIP"
export type PlatformWorkMode = "ONSITE" | "REMOTE" | "HYBRID"
export interface PlatformJobItem {
  jobId: string; publicId: string; tenantId: string; frozenCompanyName: string | null; title: string
  status: PlatformJobStatus; department: string | null; location: string | null; language: string
  employmentType: PlatformEmploymentType | null; workMode: PlatformWorkMode | null; experienceLevel: string | null
  publishedAt: string | null; closingAt: string | null; updatedAt: string; discoverable: boolean
  visibleOnPublicBoard: boolean; totalApplications: number; totalInterviews: number
}
export interface PlatformJobPage { items: PlatformJobItem[]; page: number; size: number; total: number }
export interface PlatformJobDetail extends PlatformJobItem { verifiedApplications: number; completedInterviews: number; unsuccessfulInterviews: number }
export type DiagnosticStatus = "UP" | "DEGRADED" | "DOWN" | "DISABLED" | "UNKNOWN"
export type DiagnosticComponent = "BUSINESS_API_JVM" | "POSTGRESQL" | "REDIS" | "RABBITMQ" | "AI_API" | "GRAPH_SERVICE" | "QDRANT" | "OLLAMA" | "RERANKER" | "SEAWEEDFS" | "CLAMAV" | "DOCUMENT_WORKER" | "PUBLIC_EDGE"
export type DiagnosticErrorCode = "TIMEOUT" | "CONNECTION_FAILURE" | "NOT_READY_RESPONSE" | "AUTHENTICATION_FAILURE" | "UNEXPECTED_RESPONSE" | "STORAGE_BUCKET_MISSING" | "QUEUE_MISSING" | "QUEUE_WARNING_DEPTH" | "QUEUE_CRITICAL_DEPTH" | "CONSUMERS_ABSENT" | "DLQ_NOT_EMPTY" | "PROBE_FAILURE"
export type DiagnosticQueueId = "DOCUMENT_INGESTION" | "DOCUMENT_STATUS" | "DOCUMENT_INGESTION_DLQ" | "DOCUMENT_STATUS_DLQ" | "RECRUITMENT_RESUME_ANALYSIS" | "RECRUITMENT_INTERVIEW_EVENTS" | "RECRUITMENT_RECORDING_OPERATIONS" | "RECRUITMENT_RESUME_ANALYSIS_DLQ" | "RECRUITMENT_INTERVIEW_EVENTS_DLQ" | "RECRUITMENT_RECORDING_OPERATIONS_DLQ"
export interface DiagnosticComponentResult { component: DiagnosticComponent; status: DiagnosticStatus; latencyMilliseconds: number | null; checkedAt: string; errorCode: DiagnosticErrorCode | null }
export interface PlatformRuntimeMetrics { scope: "APPLICATION_CONTAINER"; cpuScope: "JVM_PROCESS"; processCpuPercentage: number | null; availableProcessors: number; heapUsedBytes: number; heapCommittedBytes: number; heapMaxBytes: number; jvmUptimeMilliseconds: number; filesystemTotalBytes: number; filesystemUsableBytes: number }
export interface PlatformHealthSnapshot { snapshotTime: string; overallStatus: DiagnosticStatus; components: DiagnosticComponentResult[]; runtimeMetrics: PlatformRuntimeMetrics }
export interface DiagnosticQueueResult { queueId: DiagnosticQueueId; domain: "DOCUMENT" | "RECRUITMENT"; deadLetterQueue: boolean; readyCount: number; consumerCount: number; status: DiagnosticStatus; checkedAt: string; errorCode: DiagnosticErrorCode | null }
export interface PlatformQueuePage { items: DiagnosticQueueResult[]; page: number; size: number; total: number; snapshotTime: string; overallStatus: DiagnosticStatus; warningDepth: number; criticalDepth: number }

function qs(values: Record<string, string | number | undefined | null>) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== "") params.set(key, String(value)) })
  return params.toString()
}
export const getPlatformOverview = async (request: ApiRequest, days: number) => readJsonOrThrow<PlatformOverview>(await request(`${getApiBase()}/platform/overview?days=${days}`))
export const getPlatformTenants = async (request: ApiRequest, query: Record<string, string | number>) => readJsonOrThrow<TenantPage>(await request(`${getApiBase()}/platform/tenants?${qs(query)}`))
export const getPlatformTenant = async (request: ApiRequest, tenantId: string) => readJsonOrThrow<TenantDetail>(await request(`${getApiBase()}/platform/tenants/${tenantId}`))
export const getFailureSummary = async (request: ApiRequest, tenantId?: string) => readJsonOrThrow<FailureSummary>(await request(`${getApiBase()}/platform/operations/failures/summary?${qs({ tenantId })}`))
export const getFailures = async (request: ApiRequest, source: FailureSource, query: Record<string, string | number>) => readJsonOrThrow<FailurePage>(await request(`${getApiBase()}/platform/operations/failures/${source}?${qs(query)}`))
export const getPlatformJobs = async (request: ApiRequest, query: Record<string, string | number>) => readJsonOrThrow<PlatformJobPage>(await request(`${getApiBase()}/platform/jobs?${qs(query)}`))
export const getPlatformJob = async (request: ApiRequest, jobId: string) => readJsonOrThrow<PlatformJobDetail>(await request(`${getApiBase()}/platform/jobs/${jobId}`))
export const getPlatformHealth = async (request: ApiRequest) => readJsonOrThrow<PlatformHealthSnapshot>(await request(`${getApiBase()}/platform/operations/health`))
export const getPlatformQueues = async (request: ApiRequest, page = 0, size = 20) => readJsonOrThrow<PlatformQueuePage>(await request(`${getApiBase()}/platform/operations/queues?${qs({ page, size })}`))
