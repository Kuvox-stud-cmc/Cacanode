import { getApiBase } from "@/lib/auth-api";
import { readJsonOrThrow } from "@/lib/api-error";
import type { ApiRequest } from "@/lib/documents-api";

export type RecruitmentStatus = string;
export type RecruitmentJob = {
  id:string;publicId:string;title:string;description:string;department:string|null;location:string|null;
  employmentType:string|null;workMode:string|null;experienceLevel:string|null;language:"vi-VN"|"en-US";
  status:RecruitmentStatus;cvPolicy:string;templateRevisionId:string|null;closingAt:string|null;
  publishedAt:string|null;companySlug:string|null;screeningQuestions:Array<{questionId:string;prompt:string}>;
  createdAt:string;updatedAt:string;
};
export type RecruitmentCandidate = {id:string;fullName:string;email:string;phone:string|null;notes:string|null;createdAt:string;updatedAt:string};
export type RecruitmentApplication = {id:string;jobId:string;jobTitle:string;candidateId:string;candidateName:string;candidateEmail:string;status:string;submittedAt:string;verifiedAt:string|null;withdrawnAt:string|null;cvPresent:boolean;cvAnalysisStatus:string;overallScore:number|null;englishBand:string|null;interviewStatus:string|null;updatedAt:string};
export type RecruitmentInterview = {id:string;applicationId:string;jobId:string;jobTitle:string;candidateId:string;candidateName:string;status:string;scheduledAt:string|null;scheduledStartAt:string|null;scheduledEndAt:string|null;schedulingTimezone:string|null;rescheduleCount:number;startedAt:string|null;completedAt:string|null;overallScore:number|null;englishBand:string|null;updatedAt:string};
export type RecruitmentTemplate = {id:string;name:string;description:string|null;locale:"vi-VN"|"en-US";archived:boolean;latestRevisionNumber:number;updatedAt:string};
export type RecruitmentOverview = {jobStatusCounts:Record<string,number>;applicationStatusCounts:Record<string,number>;interviewStatusCounts:Record<string,number>;upcomingInterviews:RecruitmentInterview[]};
export type ApplicationDetail = {application:RecruitmentApplication;candidate:RecruitmentCandidate;screeningQuestions:Array<{questionId:string;prompt:string;options:Array<{optionId:string;label:string}>}>;screeningAnswers:Array<{questionId:string;optionId:string}>};
export type CallAttempt = {attemptNumber:number;status:string;createdAt:string;updatedAt:string;answeredAt:string|null;consentedAt:string|null;terminalAt:string|null;failureCode:string|null};
export type InterviewTranscript = {deliveryStatus:string;expectedTurnCount:number;persistedTurnCount:number;page:number;size:number;turns:Array<{turnId:string;sequence:number;speaker:string;turnKind:string;sectionId:string|null;questionId:string|null;languageTag:string;transcript:string}>};
export type InterviewResult = {terminalKind:string;deliveryStatus:string;completionReason:string|null;failureCode:string|null;partial:boolean;overallScore:number|null;englishBand:string|null;advisoryOnly:boolean;englishWarning:string;sections:Array<{sectionId:string;kind:string;status:string;questions:Array<{questionId:string;status:string;score:number|null;evidenceTurnIds:string[]}>}>};
export type InterviewRecording = {recordingId:string;state:string;contentType:string|null;sizeBytes:number|null;retainedUntil:string|null;readyAt:string|null;deletedAt:string|null};
export type RecruitmentSettings = {defaultAutomationMode:string;cvAiMode:string;defaultTemplateRevisionId:string|null;recordingEnabled:boolean;recordingRetentionDays:number;schedulingTimezone:string;minimumNoticeMinutes:number;bookingHorizonDays:number;invitationLifetimeDays:number;rescheduleCutoffMinutes:number;reminderOffsetsMinutes:number[];version:number};
export type Availability = {timezone:string;weeklyWindows:Array<{dayOfWeek:number;startLocal:string;endLocal:string}>;exceptions:Array<{date:string;kind:string;startLocal:string;endLocal:string}>;version:number};
export type RecruitmentCapabilities = {tenantId:string;rolloutStage:"OFF"|"INTERNAL"|"PILOT"|"GA";masterEnabled:boolean;publicJobsEnabled:boolean;automationEnabled:boolean;cvAiEnabled:boolean;callingEnabled:boolean;recordingEnabled:boolean;publicDiscoveryEnabled:boolean;blockers:string[]};

const base = () => `${getApiBase()}/recruitment`;
const params = (query:Record<string,string|number|boolean|null|undefined>) => {const value=new URLSearchParams();Object.entries(query).forEach(([key,item])=>{if(item!==null&&item!==undefined&&item!=="")value.set(key,String(item));});return value;};
async function list<T>(request:ApiRequest,path:string,query:Record<string,string|number|boolean|null|undefined>={}) {const response=await request(`${base()}${path}?${params(query)}`);const items=await readJsonOrThrow<T[]>(response);return {items,total:Number(response.headers.get("X-Total-Count")??items.length)};}
async function json<T>(request:ApiRequest,path:string,init?:RequestInit){return readJsonOrThrow<T>(await request(`${base()}${path}`,init));}

export const getRecruitmentOverview=(request:ApiRequest,signal?:AbortSignal)=>json<RecruitmentOverview>(request,"/overview",{signal});
export const getRecruitmentCapabilities=(request:ApiRequest,signal?:AbortSignal)=>json<RecruitmentCapabilities>(request,"/capabilities",{signal});
export const listRecruitmentJobs=(request:ApiRequest,query:Record<string,string|number|undefined>)=>list<RecruitmentJob>(request,"/jobs",query);
export const listRecruitmentCandidates=(request:ApiRequest,query:Record<string,string|number|undefined>)=>list<RecruitmentCandidate>(request,"/candidates",query);
export const listRecruitmentApplications=(request:ApiRequest,query:Record<string,string|number|boolean|undefined>)=>list<RecruitmentApplication>(request,"/applications",query);
export const listRecruitmentInterviews=(request:ApiRequest,query:Record<string,string|number|undefined>)=>list<RecruitmentInterview>(request,"/interviews",query);
export const listRecruitmentTemplates=(request:ApiRequest,query:Record<string,string|number|boolean|undefined>)=>list<RecruitmentTemplate>(request,"/templates",query);
export const getApplicationDetail=(request:ApiRequest,id:string)=>json<ApplicationDetail>(request,`/applications/${id}/detail`);
export const getInterviewAttempts=(request:ApiRequest,id:string)=>json<CallAttempt[]>(request,`/interviews/${id}/attempts`);
export const getInterviewTranscript=(request:ApiRequest,id:string,page=0,size=100)=>json<InterviewTranscript>(request,`/interviews/${id}/transcript?page=${page}&size=${size}`);
export const getInterviewResult=(request:ApiRequest,id:string)=>json<InterviewResult>(request,`/interviews/${id}/result`);
export const getInterviewRecordings=(request:ApiRequest,id:string)=>json<InterviewRecording[]>(request,`/interviews/${id}/recordings`);
export async function getRecordingBlob(request:ApiRequest,interviewId:string,recordingId:string){const response=await request(recordingUrl(interviewId,recordingId));if(!response.ok)throw new Error("Recording is not available");return response.blob();}
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
