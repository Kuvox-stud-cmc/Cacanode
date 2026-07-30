"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useTranslations } from "next-intl";
import {
  exchangeCandidateToken, exchangeInterviewInvitation, getCandidateApplication,
  getCandidateCompletion, completeCandidateApplication,
  getInterviewSlots, refreshCandidateSession, scheduleInterview, withdrawCandidateApplication,
  withdrawInterviewInvitation, requestCandidatePrivacyDeletion, confirmCandidatePrivacyDeletion,
  type CandidateApplication, type CandidateSession, type CandidateCompletionDetails, type InterviewSlot, type InvitationDetails,
} from "@/lib/recruitment-api";
import { Button } from "@/components/ui/button";
import { useRecruitmentConfirmation } from "@/components/recruitment/useRecruitmentConfirmation";
import { CalendarDays, Clock3, Globe2 } from "lucide-react";

type InitialManagementLoad =
  | { kind: "candidate-session"; session: CandidateSession }
  | { kind: "candidate-current"; application: CandidateApplication }
  | { kind: "invitation"; session: Awaited<ReturnType<typeof exchangeInterviewInvitation>> }
  | { kind: "privacy-deletion" };

export function consumeCandidateAccessParameters() {
  const query=new URLSearchParams(window.location.search);const fragment=new URLSearchParams(window.location.hash.slice(1));
  const values={
    invitation:query.get("invitation")??fragment.get("invitation"),
    token:query.get("token")??fragment.get("token"),
    deletion:query.get("deletion")??fragment.get("deletion"),
  };
  if(values.invitation||values.token||values.deletion){query.delete("invitation");query.delete("token");query.delete("deletion");const remaining=query.toString();window.history.replaceState(null,"",window.location.pathname+(remaining?`?${remaining}`:""));}
  return values;
}

export function CandidateManagement(){
  const t=useTranslations("Jobs.manage");
  const [application,setApplication]=useState<CandidateApplication|null>(null);const [csrf,setCsrf]=useState("");
  const [completion,setCompletion]=useState<CandidateCompletionDetails|null>(null);
  const [invitation,setInvitation]=useState<InvitationDetails|null>(null);const [invitationCsrf,setInvitationCsrf]=useState("");
  const [error,setError]=useState<string|null>(null);const [loading,setLoading]=useState(true);
  const [privacyMessage,setPrivacyMessage]=useState<string|null>(null);
  const initialLoad=useRef<Promise<InitialManagementLoad>|null>(null);const invitationFlow=useRef(false);
  useEffect(()=>{let active=true;
    if(!initialLoad.current)initialLoad.current=(async()=>{const {invitation:interviewToken,token,deletion}=consumeCandidateAccessParameters();
      if(deletion){await confirmCandidatePrivacyDeletion(deletion);return {kind:"privacy-deletion"} as const;}
      if(interviewToken){invitationFlow.current=true;return {kind:"invitation",session:await exchangeInterviewInvitation(interviewToken)} as const;}
      if(token){return {kind:"candidate-session",session:await exchangeCandidateToken(token)} as const;}
      try{return {kind:"candidate-current",application:await getCandidateApplication()} as const;}catch{return {kind:"candidate-session",session:await refreshCandidateSession()} as const;}
    })();
    initialLoad.current.then(result=>{if(!active)return;if(result.kind==="privacy-deletion")setPrivacyMessage(t("deletionConfirmed"));else if(result.kind==="invitation"){setInvitationCsrf(result.session.csrfToken);setInvitation(result.session.invitation);}else if(result.kind==="candidate-current")setApplication(result.application);else{setApplication(result.session.application);setCsrf(result.session.csrfToken);}})
      .catch(e=>{if(!active)return;const message=e instanceof Error?e.message:"";setError(invitationFlow.current&&message.includes("INVITATION_EXPIRED")?t("invitation.expired"):message.toLowerCase().includes("invalid")||message.toLowerCase().includes("expired")?t("invalid"):t("failed"));})
      .finally(()=>{if(active)setLoading(false);});
    return()=>{active=false};
  },[t]);
  useEffect(()=>{if(application?.status!=="AWAITING_CANDIDATE"||!csrf)return;let active=true;getCandidateCompletion().then(value=>{if(active)setCompletion(value)}).catch(()=>{if(active)setError(t("failed"))});return()=>{active=false};},[application?.status,csrf,t]);
  async function withdraw(){if(!csrf){try{const session=await refreshCandidateSession();setCsrf(session.csrfToken);setApplication(await withdrawCandidateApplication(session.csrfToken));}catch{setError(t("invalid"));}return;}try{setApplication(await withdrawCandidateApplication(csrf));}catch{setError(t("failed"));}}
  async function requestDeletion(){let value=csrf;try{if(!value){const session=await refreshCandidateSession();value=session.csrfToken;setCsrf(value);}await requestCandidatePrivacyDeletion(value);setPrivacyMessage(t("deletionRequested"));}catch{setError(t("failed"));}}
  if(loading)return <p role="status" className="text-slate-600">{t("loading")}</p>;
  if(error)return <div role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-amber-900">{error}</div>;
  if(privacyMessage&&!application)return <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 p-6 text-emerald-950">{privacyMessage}</div>;
  if(invitation&&invitationCsrf)return <InvitationScheduler csrf={invitationCsrf} initial={invitation}/>;
  if(!application)return <div role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-amber-900">{t("invalid")}</div>;
  if(application.status==="AWAITING_CANDIDATE")return completion?<CandidateCompletionForm csrf={csrf} details={completion} onCompleted={value=>{setApplication(value);setCompletion(null)}}/>:<p role="status" className="text-slate-600">{t("loading")}</p>;
  return <section className="rounded-xl border bg-white p-6 shadow-sm"><p className="text-sm font-medium text-indigo-600">{application.companyName}</p><h1 className="mt-1 text-2xl font-bold">{application.jobTitle}</h1>{privacyMessage&&<p role="status" className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-950">{privacyMessage}</p>}<dl className="mt-6 grid gap-4 text-sm sm:grid-cols-2"><div><dt className="text-slate-500">{t("status")}</dt><dd className="font-medium">{t(`statuses.${application.status}`)}</dd></div><div><dt className="text-slate-500">{t("submitted")}</dt><dd>{application.submittedAt?new Intl.DateTimeFormat(undefined,{dateStyle:"medium"}).format(new Date(application.submittedAt)):"—"}</dd></div><div><dt className="text-slate-500">{t("cv")}</dt><dd>{application.cvPresent?t("cvPresent"):t("cvAbsent")}</dd></div></dl><div className="mt-8 flex flex-wrap gap-3">{application.status!=="WITHDRAWN"&&<Button variant="destructive" onClick={withdraw}>{t("withdraw")}</Button>}<Button variant="destructive" onClick={requestDeletion}>{t("deleteData")}</Button></div></section>;
}

function CandidateCompletionForm({csrf,details,onCompleted}:{csrf:string;details:CandidateCompletionDetails;onCompleted:(value:CandidateApplication)=>void}){
  const t=useTranslations("Jobs.manage.completion");
  const [fullName,setFullName]=useState(details.fullName);const [phone,setPhone]=useState(details.phone??"");
  const [locale,setLocale]=useState<"vi-VN"|"en-US">(details.locale);const [privacy,setPrivacy]=useState(false);
  const [cvConsent,setCvConsent]=useState(false);const [answers,setAnswers]=useState<Record<string,string>>({});
  const [cv,setCv]=useState<File|null>(null);const [busy,setBusy]=useState(false);const [error,setError]=useState("");
  async function submit(event:FormEvent){event.preventDefault();setError("");
    if(!privacy||details.screeningQuestions.some(q=>!answers[q.questionId])||(details.cvPolicy==="REQUIRED"&&!cv)){setError(t("required"));return;}
    setBusy(true);try{onCompleted(await completeCandidateApplication(csrf,{fullName,phone,locale,privacyConsent:privacy,cvUseConsent:cvConsent,screeningAnswers:details.screeningQuestions.map(q=>({questionId:q.questionId,optionId:answers[q.questionId]}))},cv));}catch(cause){setError(cause instanceof Error?cause.message:t("failed"));}finally{setBusy(false);}
  }
  return <form onSubmit={submit} className="rounded-xl border bg-white p-6 shadow-sm space-y-6"><div><p className="text-sm font-medium text-indigo-600">{details.companyName}</p><h2 className="text-2xl font-bold">{t("title",{job:details.jobTitle})}</h2><p className="mt-1 text-sm text-slate-600">{details.email}</p></div>
    <label className="block text-sm font-medium">{t("fullName")}<input className="mt-1 w-full rounded-md border px-3 py-2" value={fullName} onChange={e=>setFullName(e.target.value)} required maxLength={200}/></label>
    <label className="block text-sm font-medium">{t("phone")}<input className="mt-1 w-full rounded-md border px-3 py-2" value={phone} onChange={e=>setPhone(e.target.value)} required placeholder="+84901234567"/></label>
    <label className="block text-sm font-medium">{t("language")}<select className="mt-1 w-full rounded-md border px-3 py-2" value={locale} onChange={e=>setLocale(e.target.value as "vi-VN"|"en-US")}><option value="en-US">English</option><option value="vi-VN">Tiếng Việt</option></select></label>
    {details.screeningQuestions.map(question=><fieldset key={question.questionId} className="space-y-2"><legend className="font-medium">{question.prompt}</legend>{question.options.map(option=><label key={option.optionId} className="flex gap-2"><input type="radio" name={question.questionId} value={option.optionId} checked={answers[question.questionId]===option.optionId} onChange={()=>setAnswers(current=>({...current,[question.questionId]:option.optionId}))}/><span>{option.label}</span></label>)}</fieldset>)}
    {details.cvPolicy!=="DISABLED"&&<label className="block text-sm font-medium">{t(details.cvPolicy==="REQUIRED"?"cvRequired":"cvOptional")}<input className="mt-1 block w-full" type="file" accept=".pdf,.doc,.docx" onChange={e=>setCv(e.target.files?.[0]??null)}/></label>}
    {details.cvAiMode!=="OFF"&&<label className="flex gap-2"><input type="checkbox" checked={cvConsent} onChange={e=>setCvConsent(e.target.checked)}/><span>{t("cvConsent")}</span></label>}
    <label className="flex gap-2"><input type="checkbox" checked={privacy} onChange={e=>setPrivacy(e.target.checked)} required/><span>{t("privacy")}</span></label>
    {error&&<p role="alert" className="rounded-md bg-red-50 p-3 text-red-700">{error}</p>}<Button type="submit" disabled={busy}>{busy?t("submitting"):t("submit")}</Button>
  </form>;
}

export function InvitationScheduler({csrf,initial}:{csrf:string;initial:InvitationDetails}){
  const t=useTranslations("Jobs.manage.invitation");const [details,setDetails]=useState(initial);const [slots,setSlots]=useState<InterviewSlot[]>([]);
  const {confirm,confirmationDialog}=useRecruitmentConfirmation();
  const [nextFrom,setNextFrom]=useState<string|null>(null);const [showSlots,setShowSlots]=useState(initial.status==="INVITED");
  const [loadingSlots,setLoadingSlots]=useState(initial.status==="INVITED");
  const [selectedDate,setSelectedDate]=useState<string|null>(null);
  const [busy,setBusy]=useState(false);const [error,setError]=useState<string|null>(null);
  useEffect(()=>{if(!showSlots)return;let active=true;getInterviewSlots().then(page=>{if(active){setSlots(page.items);setNextFrom(page.nextFrom);}}).catch(e=>active&&setError(t(messageKey(e)))).finally(()=>{if(active)setLoadingSlots(false);});return()=>{active=false};},[showSlots,t]);
  const days=useMemo(()=>groupInterviewSlotsByLocalDate(slots),[slots]);
  const activeDate=selectedDate&&days.some(day=>day.key===selectedDate)?selectedDate:days[0]?.key??null;
  const selectedDay=days.find(day=>day.key===activeDate)??null;
  async function choose(slot:InterviewSlot){const rescheduling=details.status==="SCHEDULED";if(!await confirm({title:t("confirmTimeTitle"),description:t(rescheduling?"confirmReschedule":"confirmSchedule"),confirmLabel:t(rescheduling?"reschedule":"schedule")}))return;setBusy(true);setError(null);try{const updated=await scheduleInterview(csrf,slot.startAt,rescheduling);setDetails(updated);setShowSlots(false);setSlots([]);}catch(e){setError(t(messageKey(e)));}finally{setBusy(false);}}
  async function more(){if(!nextFrom)return;setBusy(true);try{const page=await getInterviewSlots(nextFrom);setSlots(current=>[...current,...page.items]);setNextFrom(page.nextFrom);}catch(e){setError(t(messageKey(e)));}finally{setBusy(false);}}
  async function withdraw(){if(!await confirm({title:t("withdrawTitle"),description:t("confirmWithdraw"),confirmLabel:t("withdraw"),destructive:true}))return;setBusy(true);try{setDetails(await withdrawInterviewInvitation(csrf));setShowSlots(false);}catch(e){setError(t(messageKey(e)));}finally{setBusy(false);}}
  const browserTimezone=Intl.DateTimeFormat().resolvedOptions().timeZone;
  const time=(value:string)=>new Intl.DateTimeFormat(undefined,{dateStyle:"medium",timeStyle:"short",timeZone:Intl.DateTimeFormat().resolvedOptions().timeZone}).format(new Date(value));
  return <section className="overflow-hidden rounded-2xl border bg-white shadow-sm"><header className="border-b bg-gradient-to-br from-indigo-50 via-white to-sky-50 p-6 sm:p-8"><p className="text-sm font-semibold text-indigo-700">{details.companyName}</p><h1 className="mt-1 text-2xl font-bold text-slate-950 sm:text-3xl">{details.jobTitle}</h1><p className="mt-3 max-w-2xl text-slate-600">{t("hello",{name:details.candidateName})}</p></header><div className="p-6 sm:p-8">
    {details.scheduledStartAt&&details.status==="SCHEDULED"&&<div className="mt-6 rounded-lg bg-emerald-50 p-4 text-emerald-950"><p className="font-medium">{t("scheduled")}</p><p>{time(details.scheduledStartAt)}</p><p className="mt-1 text-xs">{t("browserTimezone",{timezone:Intl.DateTimeFormat().resolvedOptions().timeZone})}</p></div>}
    {details.status==="CANCELLED"&&<p className="mt-6 rounded-lg bg-slate-100 p-4">{t("withdrawn")}</p>}
    {showSlots&&<div><div className="flex items-center gap-2 text-sm text-slate-600"><Globe2 className="size-4" aria-hidden="true"/><span>{t("timezoneNote",{timezone:browserTimezone})}</span></div><h2 className="mt-6 text-xl font-semibold text-slate-950">{t("chooseSlot")}</h2>{loadingSlots&&<p role="status" className="mt-4 text-slate-600">{t("loading")}</p>}{days.length===0&&!busy&&!loadingSlots&&!error&&<p className="mt-4 rounded-lg bg-amber-50 p-4 text-amber-900">{t("empty")}</p>}{days.length>0&&<div className="mt-6 grid gap-7 lg:grid-cols-[minmax(0,1.15fr)_minmax(18rem,0.85fr)]"><div><h3 className="flex items-center gap-2 text-sm font-semibold text-slate-700"><span className="flex size-6 items-center justify-center rounded-full bg-indigo-600 text-xs text-white">1</span><CalendarDays className="size-4" aria-hidden="true"/>{t("dateStep")}</h3><div className="mt-3 flex gap-2 overflow-x-auto pb-2 lg:grid lg:grid-cols-3 lg:overflow-visible">{days.map(day=>{const selected=day.key===activeDate;return <button key={day.key} type="button" aria-pressed={selected} onClick={()=>setSelectedDate(day.key)} className={`min-w-28 rounded-xl border px-3 py-3 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 ${selected?"border-indigo-600 bg-indigo-600 text-white shadow-sm":"border-slate-200 bg-white text-slate-800 hover:border-indigo-300 hover:bg-indigo-50"}`}><span className={`block text-xs font-medium uppercase tracking-wide ${selected?"text-indigo-100":"text-slate-500"}`}>{day.weekday}</span><span className="mt-1 block text-lg font-bold">{day.dayMonth}</span><span className={`mt-1 block text-xs ${selected?"text-indigo-100":"text-slate-500"}`}>{t("slotsAvailable",{count:day.slots.length})}</span></button>;})}</div>{nextFrom&&<Button className="mt-4" variant="outline" disabled={busy} onClick={more}>{t("loadMore")}</Button>}</div><div className="lg:border-l lg:pl-7"><h3 className="flex items-center gap-2 text-sm font-semibold text-slate-700"><span className="flex size-6 items-center justify-center rounded-full bg-indigo-600 text-xs text-white">2</span><Clock3 className="size-4" aria-hidden="true"/>{t("timeStep")}</h3>{selectedDay&&<><p className="mt-3 font-medium text-slate-900">{selectedDay.fullDate}</p><div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-2 xl:grid-cols-3">{selectedDay.slots.map(slot=><Button key={slot.startAt} data-testid={`interview-slot-${slot.startAt}`} variant="outline" className="h-11 justify-center text-base" disabled={busy} onClick={()=>choose(slot)}>{new Intl.DateTimeFormat(undefined,{timeStyle:"short"}).format(new Date(slot.startAt))}</Button>)}</div></>}</div></div>}</div>}
    {error&&<p role="alert" className="mt-5 rounded-lg bg-red-50 p-4 text-red-700">{error}</p>}
    <div className="mt-8 flex flex-wrap gap-3">{details.status==="SCHEDULED"&&!showSlots&&<Button variant="outline" onClick={()=>{setLoadingSlots(true);setError(null);setShowSlots(true);}}>{t("reschedule")}</Button>}{details.status!=="CANCELLED"&&<Button variant="destructive" disabled={busy} onClick={withdraw}>{t("withdraw")}</Button>}</div>
    {confirmationDialog}
  </div></section>;
}

export function groupInterviewSlotsByLocalDate(slots:InterviewSlot[]){
  const groups=new Map<string,InterviewSlot[]>();
  for(const slot of slots){const date=new Date(slot.startAt);const key=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`;const existing=groups.get(key);if(existing)existing.push(slot);else groups.set(key,[slot]);}
  return [...groups.entries()].map(([key,items])=>{const date=new Date(items[0].startAt);return {key,slots:items,weekday:new Intl.DateTimeFormat(undefined,{weekday:"short"}).format(date),dayMonth:new Intl.DateTimeFormat(undefined,{day:"numeric",month:"short"}).format(date),fullDate:new Intl.DateTimeFormat(undefined,{dateStyle:"full"}).format(date)};});
}

export function messageKey(error:unknown):"slotUnavailable"|"quota"|"cutoff"|"availability"|"failed"{const value=error instanceof Error?error.message:"";if(value.includes("SLOT_UNAVAILABLE"))return "slotUnavailable";if(value.includes("INTERVIEW_QUOTA_EXHAUSTED"))return "quota";if(value.includes("RESCHEDULE_CUTOFF"))return "cutoff";if(value.includes("INTERVIEW_AVAILABILITY_NOT_CONFIGURED"))return "availability";return "failed";}
