import { getApiBase } from "@/lib/auth-api";

export type PublicJob = {
  publicId: string;
  tenantSlug: string;
  companyName: string;
  title: string;
  description: string;
  descriptionHtml: string | null;
  department: string | null;
  location: string | null;
  employmentType: "FULL_TIME" | "PART_TIME" | "CONTRACT" | "TEMPORARY" | "INTERNSHIP" | null;
  workMode: "ONSITE" | "REMOTE" | "HYBRID" | null;
  experienceLevel: "ENTRY" | "JUNIOR" | "MID" | "SENIOR" | "LEAD" | "EXECUTIVE" | null;
  language: "vi-VN" | "en-US";
  cvPolicy: "DISABLED" | "OPTIONAL" | "REQUIRED";
  cvAiMode: "OFF" | "SUMMARY_ONLY" | "PERSONALIZED_QUESTIONS";
  cvAiDisclosed: boolean;
  screeningQuestions: { questionId: string; prompt: string; options: { optionId: string; label: string }[] }[];
  publishedAt: string;
  closingAt: string;
  discoverable: boolean;
};

export type PublicJobPage = { items: PublicJob[]; nextCursor: string | null };
export type CandidateApplication = {
  applicationId: string;
  jobPublicId: string;
  companyName: string;
  jobTitle: string;
  status: "AWAITING_CANDIDATE" | "SUBMITTED_UNVERIFIED" | "SUBMITTED" | "INTERVIEW_INVITED" | "INTERVIEW_SCHEDULED" | "INTERVIEW_COMPLETED" | "UNDER_REVIEW" | "SHORTLISTED" | "REJECTED" | "WITHDRAWN";
  submittedAt: string | null;
  verifiedAt: string | null;
  withdrawnAt: string | null;
  cvPresent: boolean;
};
export type CandidateSession = { csrfToken: string; application: CandidateApplication };
export type CandidateCompletionDetails = {
  applicationId:string;companyName:string;jobTitle:string;fullName:string;email:string;phone:string|null;
  locale:"vi-VN"|"en-US";cvPolicy:"DISABLED"|"OPTIONAL"|"REQUIRED";
  cvAiMode:"OFF"|"SUMMARY_ONLY"|"PERSONALIZED_QUESTIONS";
  screeningQuestions:Array<{questionId:string;prompt:string;options:Array<{optionId:string;label:string}>}>;
};
export type InvitationDetails = {
  interviewId: string; companyName: string; jobTitle: string; candidateName: string;
  status: "INVITED" | "SCHEDULED" | "CANCELLED" | "EXPIRED";
  scheduledStartAt: string | null; scheduledEndAt: string | null; schedulingTimezone: string | null;
  invitationExpiresAt: string | null; rescheduleCount: number;
};
export type InterviewSlot = { startAt: string; endAt: string; schedulingTimezone: string };
export type InterviewSlotPage = { items: InterviewSlot[]; nextFrom: string | null; schedulingTimezone: string };

async function json<T>(response: Response): Promise<T> {
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const message = body && typeof body === "object" && "message" in body ? String(body.message) : "Request failed";
    throw new Error(message);
  }
  return body as T;
}

export async function listPublicJobs(params: URLSearchParams, tenantSlug?: string): Promise<PublicJobPage> {
  const route = tenantSlug ? `/public/careers/${encodeURIComponent(tenantSlug)}/jobs` : "/public/jobs";
  return json<PublicJobPage>(await fetch(`${getApiBase()}${route}?${params.toString()}`, { next: { revalidate: 60 } }));
}

export async function getPublicJob(publicId: string): Promise<PublicJob> {
  return json<PublicJob>(await fetch(`${getApiBase()}/public/jobs/${encodeURIComponent(publicId)}`, { next: { revalidate: 60 } }));
}

export async function submitApplication(publicId: string, application: object, cv: File | null, turnstileToken: string) {
  const form = new FormData();
  form.append("application", new Blob([JSON.stringify(application)], { type: "application/json" }));
  if (cv) form.append("cv", cv);
  if (turnstileToken) form.append("turnstileToken", turnstileToken);
  return json<{ accepted: boolean; message: string }>(await fetch(
    `${getApiBase()}/public/jobs/${encodeURIComponent(publicId)}/applications`,
    { method: "POST", body: form, cache: "no-store" },
  ));
}

export async function exchangeCandidateToken(token: string): Promise<CandidateSession> {
  return json<CandidateSession>(await fetch(`${getApiBase()}/public/applications/access`, {
    method: "POST", headers: { "Content-Type": "application/json" }, credentials: "include",
    cache: "no-store", body: JSON.stringify({ token }),
  }));
}
export async function getCandidateApplication(): Promise<CandidateApplication> {
  return json<CandidateApplication>(await fetch(`${getApiBase()}/public/applications/me`, { credentials: "include", cache: "no-store" }));
}
export async function getCandidateCompletion():Promise<CandidateCompletionDetails>{
  return json(await fetch(`${getApiBase()}/public/applications/me/completion`,{credentials:"include",cache:"no-store"}));
}
export async function completeCandidateApplication(csrfToken:string,application:object,cv:File|null):Promise<CandidateApplication>{
  const form=new FormData();form.append("application",new Blob([JSON.stringify(application)],{type:"application/json"}));
  if(cv)form.append("cv",cv);
  return json(await fetch(`${getApiBase()}/public/applications/me/complete`,{method:"POST",credentials:"include",cache:"no-store",headers:{"X-CSRF-Token":csrfToken},body:form}));
}
export async function refreshCandidateSession(): Promise<CandidateSession> {
  return json<CandidateSession>(await fetch(`${getApiBase()}/public/applications/session/refresh`, { method: "POST", credentials: "include", cache: "no-store" }));
}
export async function withdrawCandidateApplication(csrfToken: string): Promise<CandidateApplication> {
  return json<CandidateApplication>(await fetch(`${getApiBase()}/public/applications/me/withdraw`, {
    method: "POST", credentials: "include", cache: "no-store", headers: { "X-CSRF-Token": csrfToken },
  }));
}
export async function closeCandidateSession(csrfToken: string): Promise<void> {
  const response = await fetch(`${getApiBase()}/public/applications/session`, {
    method: "DELETE", credentials: "include", cache: "no-store", headers: { "X-CSRF-Token": csrfToken },
  });
  if (!response.ok) throw new Error("Unable to close candidate session");
}
export async function requestCandidatePrivacyDeletion(csrfToken:string):Promise<{status:string}>{
  return json(await fetch(`${getApiBase()}/public/applications/me/privacy-deletion-requests`,{method:"POST",credentials:"include",cache:"no-store",headers:{"X-CSRF-Token":csrfToken}}));
}
export async function confirmCandidatePrivacyDeletion(token:string):Promise<{status:string}>{
  return json(await fetch(`${getApiBase()}/public/applications/privacy-deletion-confirmations`,{method:"POST",cache:"no-store",headers:{"Content-Type":"application/json"},body:JSON.stringify({token})}));
}

export async function exchangeInterviewInvitation(token: string): Promise<{csrfToken:string;invitation:InvitationDetails}> {
  return json(await fetch(`${getApiBase()}/public/interview-invitations/exchange`, { method:"POST", credentials:"include", cache:"no-store", headers:{"Content-Type":"application/json"}, body:JSON.stringify({token}) }));
}
export async function getInterviewInvitation(): Promise<InvitationDetails> {
  return json<InvitationDetails>(await fetch(`${getApiBase()}/public/interview-invitations/me`, { credentials:"include", cache: "no-store" }));
}
export async function getInterviewSlots(from?: string): Promise<InterviewSlotPage> {
  const query = new URLSearchParams({ days: "14" }); if (from) query.set("from", from);
  return json<InterviewSlotPage>(await fetch(`${getApiBase()}/public/interview-invitations/me/slots?${query}`, { credentials:"include", cache: "no-store" }));
}
export async function scheduleInterview(csrfToken: string, startAt: string, reschedule = false): Promise<InvitationDetails> {
  return json<InvitationDetails>(await fetch(`${getApiBase()}/public/interview-invitations/me/${reschedule ? "reschedule" : "schedule"}`, {
    method: "POST", credentials:"include", headers: { "Content-Type": "application/json", "X-CSRF-Token":csrfToken }, cache: "no-store", body: JSON.stringify({ startAt }),
  }));
}
export async function withdrawInterviewInvitation(csrfToken: string): Promise<InvitationDetails> {
  return json<InvitationDetails>(await fetch(`${getApiBase()}/public/interview-invitations/me/withdraw`, { method: "POST", credentials:"include", headers:{"X-CSRF-Token":csrfToken}, cache: "no-store" }));
}
