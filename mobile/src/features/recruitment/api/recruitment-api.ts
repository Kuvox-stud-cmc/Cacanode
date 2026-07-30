import { springApi } from '@/services/api/api';
import type { ApplicationDetail,CallAttempt,CandidateWrite,CvAnalysis,DeliveryHistoryItem,DialEligibility,DialResponse,InterviewRecording,InterviewResult,InterviewSlot,InterviewTranscript,JobWrite,RecruitmentApplication,RecruitmentAvailability,RecruitmentCandidate,RecruitmentCapabilities,RecruitmentInterview,RecruitmentJob,RecruitmentJobPreview,RecruitmentOverview,RecruitmentSettings,RecruitmentTemplate,RevisionContent,RevisionResponse,TemplateCreate } from '@/features/recruitment/types';

const base='/recruitment';
const jobList={type:'RecruitmentJob' as const,id:'LIST'};
const applicationList={type:'RecruitmentApplication' as const,id:'LIST'};
const candidateList={type:'RecruitmentCandidate' as const,id:'LIST'};
const templateList={type:'RecruitmentTemplate' as const,id:'LIST'};
const interviewList={type:'RecruitmentInterview' as const,id:'LIST'};

export const recruitmentApi=springApi.injectEndpoints({endpoints:(build)=>({
  getRecruitmentCapabilities:build.query<RecruitmentCapabilities,void>({query:()=>`${base}/capabilities`,providesTags:['RecruitmentCapabilities']}),
  getRecruitmentOverview:build.query<RecruitmentOverview,void>({query:()=>`${base}/overview`,providesTags:['RecruitmentOverview']}),
  getRecruitmentJobs:build.query<RecruitmentJob[],{status?:string;q?:string}|void>({query:(arg)=>({url:`${base}/jobs`,params:{size:100,...arg}}),providesTags:(result)=>result?[...result.map(({id})=>({type:'RecruitmentJob' as const,id})),jobList]:[jobList]}),
  getRecruitmentJob:build.query<RecruitmentJob,string>({query:(id)=>`${base}/jobs/${id}`,providesTags:(_r,_e,id)=>[{type:'RecruitmentJob',id}]}),
  getRecruitmentJobPreview:build.query<RecruitmentJobPreview,string>({query:(id)=>`${base}/jobs/${id}/preview`}),
  createRecruitmentJob:build.mutation<RecruitmentJob,JobWrite>({query:(body)=>({url:`${base}/jobs`,method:'POST',body}),invalidatesTags:[jobList,'RecruitmentOverview']}),
  updateRecruitmentJob:build.mutation<RecruitmentJob,{id:string;body:JobWrite}>({query:({id,body})=>({url:`${base}/jobs/${id}`,method:'PUT',body}),invalidatesTags:(_r,_e,{id})=>[{type:'RecruitmentJob',id},jobList,'RecruitmentOverview']}),
  deleteRecruitmentJob:build.mutation<void,string>({query:(id)=>({url:`${base}/jobs/${id}`,method:'DELETE'}),invalidatesTags:[jobList,'RecruitmentOverview']}),
  jobAction:build.mutation<RecruitmentJob,{id:string;action:'publish'|'pause'|'close'|'archive'}>({query:({id,action})=>({url:`${base}/jobs/${id}/${action}`,method:'POST'}),invalidatesTags:(_r,_e,{id})=>[{type:'RecruitmentJob',id},jobList,'RecruitmentOverview']}),

  getRecruitmentApplications:build.query<RecruitmentApplication[],{status?:string;candidateId?:string;jobId?:string;q?:string}|void>({query:(arg)=>({url:`${base}/applications`,params:{size:100,...arg}}),providesTags:(result)=>result?[...result.map(({id})=>({type:'RecruitmentApplication' as const,id})),applicationList]:[applicationList]}),
  getRecruitmentApplicationDetail:build.query<ApplicationDetail,string>({query:(id)=>`${base}/applications/${id}/detail`,providesTags:(_r,_e,id)=>[{type:'RecruitmentApplication',id}]}),
  getCvAnalysis:build.query<CvAnalysis,string>({query:(id)=>`${base}/applications/${id}/cv-analysis`,providesTags:(_r,_e,id)=>[{type:'RecruitmentCvAnalysis',id}]}),
  createRecruitmentApplication:build.mutation<RecruitmentApplication,{jobId:string;candidateId:string}>({query:(body)=>({url:`${base}/applications`,method:'POST',body}),invalidatesTags:[applicationList,'RecruitmentOverview']}),
  sendCompletionLink:build.mutation<{sent:boolean;message?:string},string>({query:(id)=>({url:`${base}/applications/${id}/completion-link`,method:'POST'})}),
  transitionApplication:build.mutation<RecruitmentApplication,{id:string;targetStatus:string}>({query:({id,targetStatus})=>({url:`${base}/applications/${id}/transitions`,method:'POST',body:{targetStatus}}),invalidatesTags:(_r,_e,{id})=>[{type:'RecruitmentApplication',id},applicationList,'RecruitmentOverview']}),
  refreshCvAnalysis:build.mutation<CvAnalysis,{id:string;requestId:string}>({query:({id,requestId})=>({url:`${base}/applications/${id}/cv-analysis/refresh`,method:'POST',body:{requestId}}),invalidatesTags:(_r,_e,{id})=>[{type:'RecruitmentCvAnalysis',id}]}),
  deleteRecruitmentCv:build.mutation<void,string>({query:(id)=>({url:`${base}/applications/${id}/cv`,method:'DELETE'}),invalidatesTags:(_r,_e,id)=>[{type:'RecruitmentApplication',id},applicationList,{type:'RecruitmentCvAnalysis',id}]}),
  inviteApplication:build.mutation<RecruitmentInterview,string>({query:(id)=>({url:`${base}/applications/${id}/invite`,method:'POST'}),invalidatesTags:[applicationList,interviewList,'RecruitmentOverview']}),

  getRecruitmentCandidates:build.query<RecruitmentCandidate[],{q?:string;jobId?:string}|void>({query:(arg)=>({url:`${base}/candidates`,params:{size:100,...arg}}),providesTags:(result)=>result?[...result.map(({id})=>({type:'RecruitmentCandidate' as const,id})),candidateList]:[candidateList]}),
  getRecruitmentCandidate:build.query<RecruitmentCandidate,string>({query:(id)=>`${base}/candidates/${id}`,providesTags:(_r,_e,id)=>[{type:'RecruitmentCandidate',id}]}),
  createRecruitmentCandidate:build.mutation<RecruitmentCandidate,CandidateWrite>({query:(body)=>({url:`${base}/candidates`,method:'POST',body}),invalidatesTags:[candidateList]}),
  updateRecruitmentCandidate:build.mutation<RecruitmentCandidate,{id:string;body:CandidateWrite}>({query:({id,body})=>({url:`${base}/candidates/${id}`,method:'PUT',body}),invalidatesTags:(_r,_e,{id})=>[{type:'RecruitmentCandidate',id},candidateList]}),
  deleteRecruitmentCandidate:build.mutation<void,string>({query:(id)=>({url:`${base}/candidates/${id}`,method:'DELETE'}),invalidatesTags:[candidateList,applicationList]}),

  getRecruitmentTemplates:build.query<RecruitmentTemplate[],{archived?:boolean;q?:string}|void>({query:(arg)=>({url:`${base}/templates`,params:{size:100,...arg}}),providesTags:(result)=>result?[...result.map(({id})=>({type:'RecruitmentTemplate' as const,id})),templateList]:[templateList]}),
  getRecruitmentTemplate:build.query<RecruitmentTemplate,string>({query:(id)=>`${base}/templates/${id}`,providesTags:(_r,_e,id)=>[{type:'RecruitmentTemplate',id}]}),
  getTemplateRevisions:build.query<RevisionResponse[],string>({query:(id)=>`${base}/templates/${id}/revisions`,providesTags:(_r,_e,id)=>[{type:'RecruitmentTemplate',id}]}),
  createRecruitmentTemplate:build.mutation<RecruitmentTemplate,TemplateCreate>({query:(body)=>({url:`${base}/templates`,method:'POST',body}),invalidatesTags:[templateList]}),
  patchRecruitmentTemplate:build.mutation<RecruitmentTemplate,{id:string;body:{name?:string;description?:string|null}}>({query:({id,body})=>({url:`${base}/templates/${id}`,method:'PATCH',body}),invalidatesTags:(_r,_e,{id})=>[{type:'RecruitmentTemplate',id},templateList]}),
  addTemplateRevision:build.mutation<RevisionResponse,{id:string;content:RevisionContent}>({query:({id,content})=>({url:`${base}/templates/${id}/revisions`,method:'POST',body:{content}}),invalidatesTags:(_r,_e,{id})=>[{type:'RecruitmentTemplate',id},templateList]}),
  archiveRecruitmentTemplate:build.mutation<void,string>({query:(id)=>({url:`${base}/templates/${id}`,method:'DELETE'}),invalidatesTags:(_r,_e,id)=>[{type:'RecruitmentTemplate',id},templateList]}),

  getRecruitmentInterviews:build.query<RecruitmentInterview[],{status?:string;jobId?:string;q?:string}|void>({query:(arg)=>({url:`${base}/interviews`,params:{size:100,...arg}}),providesTags:(result)=>result?[...result.map(({id})=>({type:'RecruitmentInterview' as const,id})),interviewList]:[interviewList]}),
  getRecruitmentInterview:build.query<RecruitmentInterview,string>({query:(id)=>`${base}/interviews/${id}`,providesTags:(_r,_e,id)=>[{type:'RecruitmentInterview',id}]}),
  getInterviewSlots:build.query<{items:InterviewSlot[];nextFrom?:string|null;schedulingTimezone:string},{id:string;from?:string}>({query:({id,from})=>({url:`${base}/interviews/${id}/slots`,params:{from}})}),
  getInterviewDeliveryHistory:build.query<DeliveryHistoryItem[],string>({query:(id)=>`${base}/interviews/${id}/delivery-history`,providesTags:(_r,_e,id)=>[{type:'RecruitmentInterview',id}]}),
  getInterviewAttempts:build.query<CallAttempt[],string>({query:(id)=>`${base}/interviews/${id}/attempts`,providesTags:(_r,_e,id)=>[{type:'RecruitmentInterview',id}]}),
  getDialEligibility:build.query<DialEligibility,string>({query:(id)=>`${base}/interviews/${id}/dial-eligibility`,providesTags:(_r,_e,id)=>[{type:'RecruitmentInterview',id}]}),
  dialInterview:build.mutation<DialResponse,string>({query:(id)=>({url:`${base}/interviews/${id}/dial`,method:'POST'}),invalidatesTags:(_r,_e,id)=>[{type:'RecruitmentInterview',id}]}),
  getInterviewTranscript:build.query<InterviewTranscript,{id:string;page?:number;size?:number}>({query:({id,page=0,size=100})=>({url:`${base}/interviews/${id}/transcript`,params:{page,size}}),providesTags:(_r,_e,{id})=>[{type:'RecruitmentInterview',id}]}),
  getInterviewResult:build.query<InterviewResult,string>({query:(id)=>`${base}/interviews/${id}/result`,providesTags:(_r,_e,id)=>[{type:'RecruitmentInterview',id}]}),
  getInterviewRecordings:build.query<InterviewRecording[],string>({query:(id)=>`${base}/interviews/${id}/recordings`,providesTags:(_r,_e,id)=>[{type:'RecruitmentRecording',id}]}),
  scheduleInterview:build.mutation<RecruitmentInterview,{id:string;startAt:string;reschedule?:boolean}>({query:({id,startAt,reschedule})=>({url:`${base}/interviews/${id}/${reschedule?'reschedule':'schedule'}`,method:'POST',body:{startAt}}),invalidatesTags:(_r,_e,{id})=>[{type:'RecruitmentInterview',id},interviewList,applicationList,'RecruitmentOverview']}),
  cancelInterview:build.mutation<RecruitmentInterview,string>({query:(id)=>({url:`${base}/interviews/${id}/cancel`,method:'POST'}),invalidatesTags:(_r,_e,id)=>[{type:'RecruitmentInterview',id},interviewList,applicationList,'RecruitmentOverview']}),
  reinviteInterview:build.mutation<RecruitmentInterview,string>({query:(id)=>({url:`${base}/interviews/${id}/reinvite`,method:'POST'}),invalidatesTags:(_r,_e,id)=>[{type:'RecruitmentInterview',id},interviewList,applicationList,'RecruitmentOverview']}),

  getRecruitmentSettings:build.query<RecruitmentSettings,void>({query:()=>`${base}/settings`,providesTags:['RecruitmentSettings']}),
  getRecruitmentAvailability:build.query<RecruitmentAvailability,void>({query:()=>`${base}/availability`,providesTags:['RecruitmentAvailability']}),
  updateRecruitmentSettings:build.mutation<RecruitmentSettings,Omit<RecruitmentSettings,'version'>>({query:(body)=>({url:`${base}/settings`,method:'PUT',body}),invalidatesTags:['RecruitmentSettings','RecruitmentCapabilities']}),
  updateRecruitmentAvailability:build.mutation<RecruitmentAvailability,Pick<RecruitmentAvailability,'version'|'weeklyWindows'|'exceptions'>>({query:(body)=>({url:`${base}/availability`,method:'PUT',body}),invalidatesTags:['RecruitmentAvailability']}),
})});

export const {useGetRecruitmentCapabilitiesQuery,useGetRecruitmentOverviewQuery,useGetRecruitmentJobsQuery,useGetRecruitmentJobQuery,useLazyGetRecruitmentJobPreviewQuery,useCreateRecruitmentJobMutation,useUpdateRecruitmentJobMutation,useDeleteRecruitmentJobMutation,useJobActionMutation,useGetRecruitmentApplicationsQuery,useGetRecruitmentApplicationDetailQuery,useGetCvAnalysisQuery,useCreateRecruitmentApplicationMutation,useSendCompletionLinkMutation,useTransitionApplicationMutation,useRefreshCvAnalysisMutation,useDeleteRecruitmentCvMutation,useInviteApplicationMutation,useGetRecruitmentCandidatesQuery,useGetRecruitmentCandidateQuery,useCreateRecruitmentCandidateMutation,useUpdateRecruitmentCandidateMutation,useDeleteRecruitmentCandidateMutation,useGetRecruitmentTemplatesQuery,useGetRecruitmentTemplateQuery,useGetTemplateRevisionsQuery,useCreateRecruitmentTemplateMutation,usePatchRecruitmentTemplateMutation,useAddTemplateRevisionMutation,useArchiveRecruitmentTemplateMutation,useGetRecruitmentInterviewsQuery,useGetRecruitmentInterviewQuery,useGetInterviewSlotsQuery,useGetInterviewDeliveryHistoryQuery,useGetInterviewAttemptsQuery,useGetDialEligibilityQuery,useDialInterviewMutation,useGetInterviewTranscriptQuery,useGetInterviewResultQuery,useGetInterviewRecordingsQuery,useScheduleInterviewMutation,useCancelInterviewMutation,useReinviteInterviewMutation,useGetRecruitmentSettingsQuery,useGetRecruitmentAvailabilityQuery,useUpdateRecruitmentSettingsMutation,useUpdateRecruitmentAvailabilityMutation}=recruitmentApi;
