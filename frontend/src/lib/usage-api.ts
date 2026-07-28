import { getApiBase } from "@/lib/auth-api";
import { readJsonOrThrow } from "@/lib/api-error";
import type { ApiRequest } from "@/lib/documents-api";
import type { DocumentStatus } from "@/types";

export type AnalyticsScope = "CUSTOMER" | "EMPLOYEE" | "ALL";
export type AnalyticsDays = 7 | 30 | 90;

export type DashboardSummary = {
  totalDocuments: number;
  documentsAddedThisWeek: number;
  userMessagesThisMonth: number;
  userMessagesPreviousMonth: number;
  storedDocumentBytes: number;
  storageLimitBytes: number;
  activeUsers: number;
  activeUsersAddedThisWeek: number;
  recentDocuments: Array<{
    id: string;
    fileName: string;
    fileType: string;
    status: DocumentStatus;
    fileSizeBytes: number;
    uploadedAt: string;
  }>;
};

export type CountMetric = { value: number; previousValue: number; percentageChange: number };
export type DurationMetric = { milliseconds: number; previousMilliseconds: number; percentageChange: number };
export type RateMetric = { percentage: number; previousPercentage: number; percentagePointChange: number };

export type AnalyticsResponse = {
  scope: AnalyticsScope;
  days: AnalyticsDays;
  periodStart: string;
  periodEnd: string;
  sessions: CountMetric;
  averageAssistantResponseTime: DurationMetric;
  closedSessionRate: RateMetric;
  userMessages: CountMetric;
  resolvedTicketRate: RateMetric | null;
  dailyMessageVolume: Array<{ date: string; count: number }>;
  popularQuestions: Array<{ question: string; count: number }>;
};
export type RecruitmentAnalyticsResponse = {
  days:AnalyticsDays;periodStart:string;periodEnd:string;jobsPublished:CountMetric;
  verifiedApplicationsSubmitted:CountMetric;completedInterviews:CountMetric;unsuccessfulInterviews:CountMetric;
  jobStatusDistribution:Record<string,number>;applicationStatusDistribution:Record<string,number>;
  interviewStatusDistribution:Record<string,number>;dailyApplicationVolume:Array<{date:string;count:number}>;
  dailyInterviewCompletionVolume:Array<{date:string;count:number}>;
};

export async function getDashboardSummary(request: ApiRequest, signal?: AbortSignal): Promise<DashboardSummary> {
  return readJsonOrThrow(await request(`${getApiBase()}/dashboard/summary`, { signal }));
}

export async function getAnalytics(
  request: ApiRequest,
  scope: AnalyticsScope,
  days: AnalyticsDays,
  signal?: AbortSignal,
): Promise<AnalyticsResponse> {
  const params = new URLSearchParams({ scope, days: String(days) });
  return readJsonOrThrow(await request(`${getApiBase()}/analytics?${params}`, { signal }));
}

export async function getRecruitmentAnalytics(request:ApiRequest,days:AnalyticsDays,signal?:AbortSignal):Promise<RecruitmentAnalyticsResponse>{
  return readJsonOrThrow(await request(`${getApiBase()}/analytics/recruitment?days=${days}`,{signal}));
}
