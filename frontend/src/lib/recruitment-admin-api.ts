import { getApiBase } from "@/lib/auth-api";
import { readJsonOrThrow } from "@/lib/api-error";
import type { ApiRequest } from "@/lib/documents-api";

export type RecruitmentStatus = string;
export type RecruitmentJob = {
  id:string;publicId:string;title:string;description:string;descriptionHtml:string|null;department:string|null;location:string|null;
  employmentType:string|null;workMode:string|null;experienceLevel:string|null;language:"vi-VN"|"en-US";
  status:RecruitmentStatus;cvPolicy:string;automationModeOverride:string|null;cvAiModeOverride:string|null;
  effectiveAutomationMode:string|null;effectiveCvAiMode:string|null;recordingEnabled:boolean;recordingRetentionDays:number;
  templateRevisionId:string|null;closingAt:string|null;publishedAt:string|null;pausedAt:string|null;closedAt:string|null;
  archivedAt:string|null;companyName:string|null;companySlug:string|null;version:number;screeningQuestions:ScreeningQuestion[];
  createdAt:string;updatedAt:string;
};
export type ScreeningQuestion = {questionId:string;prompt:string;options:Array<{optionId:string;label:string}>;acceptedOptionIds:string[]};
export type JobWrite = {title:string;description:string;descriptionHtml:string|null;department:string|null;location:string|null;employmentType:string|null;workMode:string|null;experienceLevel:string|null;language:"vi-VN"|"en-US";cvPolicy:string;automationModeOverride:string|null;cvAiModeOverride:string|null;templateRevisionId:string|null;closingAt:string|null;screeningQuestions:Array<ScreeningQuestion>};
export type RecruitmentJobPreview = Pick<RecruitmentJob,"publicId"|"title"|"description"|"descriptionHtml"|"department"|"location"|"employmentType"|"workMode"|"experienceLevel"|"language"|"cvPolicy"|"status"|"publishedAt"|"closingAt"> & {tenantSlug:string;companyName:string};
export type InteractionLimits = {repetitionLimit:number;clarificationLimit:number;silenceTimeoutSeconds:number;silencePromptLimit:number};
export type Question = {questionId:string;position:number;prompt:string;competency:string;rubric:string;followUpLimit:number;source:string;evidence:string|null};
export type Section = {sectionId:string;position:number;kind:string;languageTag:string;durationLimitSeconds:number;transitionText:string|null;questions:Array<Question>};
export type RevisionContent = {introductionText:string;disclosureText:string;closingText:string;durationLimitSeconds:number;interactionLimits:InteractionLimits;sections:Array<Section>};
export type TemplateCreate = {name:string;description:string|null;locale:"vi-VN"|"en-US";content:RevisionContent};
export type TemplatePatch = {name?:string;description?:string|null};
export type RevisionCreate = {content:RevisionContent};
export type RevisionResponse = {id:string;templateId:string;revisionNumber:number;content:RevisionContent;contentSha256:string;createdAt:string};
export type CvAnalysisEvidence = {anchorId:string;excerpt:string;sourceLocation:string};
export type CvAnalysisSkill = {name:string;evidenceAnchorIds:string[]};
export type CvAnalysisQuestion = {questionId:string;targetSectionId:string;prompt:string;competency:string;rubric:string;evidenceAnchorIds:string[]};
export type CvAnalysisResponse = {mode:string;status:string;policyVersion:string;modelVersion:string;generatedAt:string;summary:string;evidence:CvAnalysisEvidence[];skills:CvAnalysisSkill[];personalizedQuestions:CvAnalysisQuestion[];failureCode:string|null;advisoryOnly:boolean};
export type RecruitmentCandidate = {id:string;fullName:string;email:string;phone:string|null;notes:string|null;createdAt:string;updatedAt:string};
export type RecruitmentApplication = {id:string;jobId:string;jobTitle:string;candidateId:string;candidateName:string;candidateEmail:string;status:string;submittedAt:string;verifiedAt:string|null;withdrawnAt:string|null;cvPresent:boolean;cvAnalysisStatus:string;overallScore:number|null;englishBand:string|null;interviewStatus:string|null;updatedAt:string};
export type RecruitmentInterview = {id:string;applicationId:string;jobId:string;jobTitle:string;candidateId:string;candidateName:string;status:string;scheduledAt:string|null;scheduledStartAt:string|null;scheduledEndAt:string|null;schedulingTimezone:string|null;rescheduleCount:number;startedAt:string|null;completedAt:string|null;overallScore:number|null;englishBand:string|null;recordingEnabled:boolean;recordingRetentionDays:number;updatedAt:string};
export type RecruitmentTemplate = {id:string;name:string;description:string|null;locale:"vi-VN"|"en-US";archived:boolean;archivedAt:string|null;latestRevisionNumber:number;version:number;createdAt:string;updatedAt:string};
export type RecruitmentOverview = {jobStatusCounts:Record<string,number>;applicationStatusCounts:Record<string,number>;interviewStatusCounts:Record<string,number>;upcomingInterviews:RecruitmentInterview[]};
export type ApplicationDetail = {application:RecruitmentApplication;candidate:RecruitmentCandidate;screeningQuestions:Array<{questionId:string;prompt:string;options:Array<{optionId:string;label:string}>}>;screeningAnswers:Array<{questionId:string;optionId:string}>};
export type CallAttempt = {attemptNumber:number;status:string;createdAt:string;updatedAt:string;answeredAt:string|null;consentedAt:string|null;terminalAt:string|null;failureCode:string|null};
export type DialEligibility = {allowed:boolean;reason:string|null;windowOpensAt:string|null;windowClosesAt:string|null;serverTime:string};
export type InterviewTranscript = {deliveryStatus:string;expectedTurnCount:number;persistedTurnCount:number;page:number;size:number;turns:Array<{turnId:string;sequence:number;speaker:string;turnKind:string;sectionId:string|null;questionId:string|null;languageTag:string;transcript:string}>};
export type InterviewResult = {terminalKind:string;deliveryStatus:string;completionReason:string|null;failureCode:string|null;partial:boolean;overallScore:number|null;englishBand:string|null;advisoryOnly:boolean;englishWarning:string;sections:Array<{sectionId:string;kind:string;status:string;questions:Array<{questionId:string;status:string;score:number|null;evidenceTurnIds:string[]}>}>};
export type InterviewRecording = {recordingId:string;state:string;contentType:string|null;sizeBytes:number|null;retainedUntil:string|null;readyAt:string|null;deletedAt:string|null};
export type RecruitmentSettings = {defaultAutomationMode:string;cvAiMode:string;defaultTemplateRevisionId:string|null;recordingEnabled:boolean;recordingRetentionDays:number;schedulingTimezone:string;minimumNoticeMinutes:number;bookingHorizonDays:number;invitationLifetimeDays:number;rescheduleCutoffMinutes:number;reminderOffsetsMinutes:number[];version:number};
export type Availability = {timezone:string;weeklyWindows:Array<{dayOfWeek:number;startLocal:string;endLocal:string}>;exceptions:Array<{date:string;kind:string;startLocal:string;endLocal:string}>;version:number};
export type RecruitmentCapabilities = {tenantId:string;rolloutStage:"OFF"|"AUTO"|"INTERNAL"|"PILOT"|"GA";masterEnabled:boolean;publicJobsEnabled:boolean;automationEnabled:boolean;cvAiEnabled:boolean;callingEnabled:boolean;recordingEnabled:boolean;publicDiscoveryEnabled:boolean;maxInterviewDurationSeconds:number;blockers:string[]};

const base = () => `${getApiBase()}/recruitment`;
const params = (query:Record<string,string|number|boolean|null|undefined>) => {const value=new URLSearchParams();Object.entries(query).forEach(([key,item])=>{if(item!==null&&item!==undefined&&item!=="")value.set(key,String(item));});return value;};
async function list<T>(request:ApiRequest,path:string,query:Record<string,string|number|boolean|null|undefined>={}) {const response=await request(`${base()}${path}?${params(query)}`);const items=await readJsonOrThrow<T[]>(response);return {items,total:Number(response.headers.get("X-Total-Count")??items.length)};}
async function json<T>(request:ApiRequest,path:string,init?:RequestInit){return readJsonOrThrow<T>(await request(`${base()}${path}`,init));}
async function empty(request:ApiRequest,path:string,init?:RequestInit){const response=await request(`${base()}${path}`,init);if(!response.ok)await readJsonOrThrow<never>(response);}

export const getRecruitmentOverview=(request:ApiRequest,signal?:AbortSignal)=>json<RecruitmentOverview>(request,"/overview",{signal});
export type DeliveryHistoryItem = { id: string; type: string; status: string; recipient: string; sentAt: string | null; failureReason: string | null; createdAt: string };
export type InterviewSlot = { startAt: string; endAt: string; schedulingTimezone: string };

export const getRecruitmentCapabilities=(request:ApiRequest,signal?:AbortSignal)=>json<RecruitmentCapabilities>(request,"/capabilities",{signal});
export const listRecruitmentJobs=(request:ApiRequest,query:Record<string,string|number|undefined>)=>list<RecruitmentJob>(request,"/jobs",query);
export const listRecruitmentCandidates=(request:ApiRequest,query:Record<string,string|number|undefined>)=>list<RecruitmentCandidate>(request,"/candidates",query);
export const listRecruitmentApplications=(request:ApiRequest,query:Record<string,string|number|boolean|undefined>)=>list<RecruitmentApplication>(request,"/applications",query);
export const listRecruitmentInterviews=(request:ApiRequest,query:Record<string,string|number|undefined>)=>list<RecruitmentInterview>(request,"/interviews",query);
export const listRecruitmentTemplates=(request:ApiRequest,query:Record<string,string|number|boolean|undefined>)=>list<RecruitmentTemplate>(request,"/templates",query);
export const getApplicationDetail=(request:ApiRequest,id:string)=>json<ApplicationDetail>(request,`/applications/${id}/detail`);
export const createRecruitmentApplication=(request:ApiRequest,body:{jobId:string;candidateId:string})=>json<RecruitmentApplication>(request,"/applications",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
export const sendApplicationCompletionLink=(request:ApiRequest,id:string)=>json<{sent:boolean;message?:string}>(request,`/applications/${id}/completion-link`,{method:"POST"});
export const deleteRecruitmentApplication=(request:ApiRequest,id:string)=>empty(request,`/applications/${id}`,{method:"DELETE"});

export const getInterviewAttempts=(request:ApiRequest,id:string)=>json<CallAttempt[]>(request,`/interviews/${id}/attempts`);
export const getInterviewTranscript=(request:ApiRequest,id:string,page=0,size=100)=>json<InterviewTranscript>(request,`/interviews/${id}/transcript?page=${page}&size=${size}`);
export const getInterviewResult=(request:ApiRequest,id:string)=>json<InterviewResult>(request,`/interviews/${id}/result`);
export const getInterviewRecordings=(request:ApiRequest,id:string)=>json<InterviewRecording[]>(request,`/interviews/${id}/recordings`);
export const getInterviewDeliveryHistory=(request:ApiRequest,id:string)=>json<DeliveryHistoryItem[]>(request,`/interviews/${id}/delivery-history`);
export const getInterviewSlotsAdmin=(request:ApiRequest,id:string,from?:string)=>json<{items:InterviewSlot[];schedulingTimezone:string}>(request,`/interviews/${id}/slots${from?`?from=${encodeURIComponent(from)}`:""}`);
export const scheduleInterviewAdmin=(request:ApiRequest,id:string,startAt:string)=>json<RecruitmentInterview>(request,`/interviews/${id}/schedule`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({startAt})});
export const rescheduleInterviewAdmin=(request:ApiRequest,id:string,startAt:string)=>json<RecruitmentInterview>(request,`/interviews/${id}/reschedule`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({startAt})});
export const cancelInterviewAdmin=(request:ApiRequest,id:string)=>json<RecruitmentInterview>(request,`/interviews/${id}/cancel`,{method:"POST"});
export const reinviteInterview=(request:ApiRequest,id:string)=>json<RecruitmentInterview>(request,`/interviews/${id}/reinvite`,{method:"POST"});
export const getDialEligibility=(request:ApiRequest,id:string)=>json<DialEligibility>(request,`/interviews/${id}/dial-eligibility`);
export const dialInterview=(request:ApiRequest,id:string)=>json<{attemptId:string;status:string;failureCode:string|null;acceptedAt:string}>(request,`/interviews/${id}/dial`,{method:"POST"});

export async function getRecordingBlob(request:ApiRequest,interviewId:string,recordingId:string,download=false){const response=await request(recordingUrl(interviewId,recordingId,download),{cache:"no-store"});if(!response.ok)throw new Error("Recording is not available");return response.blob();}
export const getRecruitmentSettings=(request:ApiRequest)=>json<RecruitmentSettings>(request,"/settings");
export const getRecruitmentAvailability=(request:ApiRequest)=>json<Availability>(request,"/availability");
export const updateRecruitmentSettings=(request:ApiRequest,value:Omit<RecruitmentSettings,"version">)=>json<RecruitmentSettings>(request,"/settings",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(value)});
export const updateRecruitmentAvailability=(request:ApiRequest,value:Pick<Availability,"version"|"weeklyWindows"|"exceptions">)=>json<Availability>(request,"/availability",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(value)});
export const transitionApplication=(request:ApiRequest,id:string,targetStatus:string)=>json<RecruitmentApplication>(request,`/applications/${id}/transitions`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({targetStatus})});
export const inviteApplication=(request:ApiRequest,id:string)=>json<RecruitmentInterview>(request,`/applications/${id}/invite`,{method:"POST"});
export const jobAction=(request:ApiRequest,id:string,action:"publish"|"pause"|"close"|"archive")=>json<RecruitmentJob>(request,`/jobs/${id}/${action}`,{method:"POST"});
export const saveCandidate=(request:ApiRequest,value:{id?:string;fullName:string;email:string;phone:string|null;notes:string|null})=>json<RecruitmentCandidate>(request,value.id?`/candidates/${value.id}`:"/candidates",{method:value.id?"PUT":"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(value)});
export const cvUrl=(applicationId:string)=>`${getApiBase()}/recruitment/applications/${applicationId}/cv`;
export const recordingUrl=(interviewId:string,recordingId:string,download=false)=>`${getApiBase()}/recruitment/interviews/${interviewId}/recordings/${recordingId}/${download?"download":"playback"}`;

export const getRecruitmentJob=(request:ApiRequest,id:string)=>json<RecruitmentJob>(request,`/jobs/${id}`);
export const getRecruitmentJobPreview=(request:ApiRequest,id:string)=>json<RecruitmentJobPreview>(request,`/jobs/${id}/preview`,{cache:"no-store"});
export const createRecruitmentJob=(request:ApiRequest,body:JobWrite)=>json<RecruitmentJob>(request,"/jobs",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
export const updateRecruitmentJob=(request:ApiRequest,id:string,body:JobWrite)=>json<RecruitmentJob>(request,`/jobs/${id}`,{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
export const deleteRecruitmentJob=(request:ApiRequest,id:string)=>empty(request,`/jobs/${id}`,{method:"DELETE"});

export const getRecruitmentTemplate=(request:ApiRequest,id:string)=>json<RecruitmentTemplate>(request,`/templates/${id}`);
export const createRecruitmentTemplate=(request:ApiRequest,body:TemplateCreate)=>json<RecruitmentTemplate>(request,"/templates",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
export const patchRecruitmentTemplate=(request:ApiRequest,id:string,body:TemplatePatch)=>json<RecruitmentTemplate>(request,`/templates/${id}`,{method:"PATCH",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
export const archiveRecruitmentTemplate=(request:ApiRequest,id:string)=>empty(request,`/templates/${id}`,{method:"DELETE"});
export const listTemplateRevisions=(request:ApiRequest,id:string)=>json<RevisionResponse[]>(request,`/templates/${id}/revisions`);
export const addTemplateRevision=(request:ApiRequest,id:string,body:RevisionCreate)=>json<RevisionResponse>(request,`/templates/${id}/revisions`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
export const getTemplateRevision=(request:ApiRequest,id:string,revisionId:string)=>json<RevisionResponse>(request,`/templates/${id}/revisions/${revisionId}`);

export const getRecruitmentCandidate=(request:ApiRequest,id:string)=>json<RecruitmentCandidate>(request,`/candidates/${id}`);
export const deleteRecruitmentCandidate=(request:ApiRequest,id:string)=>empty(request,`/candidates/${id}`,{method:"DELETE"});

export const getRecruitmentApplication=(request:ApiRequest,id:string)=>json<RecruitmentApplication>(request,`/applications/${id}`);
export const deleteRecruitmentCv=(request:ApiRequest,applicationId:string)=>empty(request,`/applications/${applicationId}/cv`,{method:"DELETE"});
export const getCvAnalysis=(request:ApiRequest,applicationId:string)=>json<CvAnalysisResponse>(request,`/applications/${applicationId}/cv-analysis`);

export const getRecruitmentInterview=(request:ApiRequest,id:string)=>json<RecruitmentInterview>(request,`/interviews/${id}`);
