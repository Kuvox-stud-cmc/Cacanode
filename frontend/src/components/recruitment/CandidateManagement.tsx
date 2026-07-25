"use client";

import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import {
  closeCandidateSession, exchangeCandidateToken, exchangeInterviewInvitation, getCandidateApplication,
  getInterviewSlots, refreshCandidateSession, scheduleInterview, withdrawCandidateApplication,
  withdrawInterviewInvitation, requestCandidatePrivacyDeletion, confirmCandidatePrivacyDeletion,
  type CandidateApplication, type InterviewSlot, type InvitationDetails,
} from "@/lib/recruitment-api";
import { Button } from "@/components/ui/button";

export function CandidateManagement(){
  const t=useTranslations("Jobs.manage");
  const [application,setApplication]=useState<CandidateApplication|null>(null);const [csrf,setCsrf]=useState("");
  const [invitation,setInvitation]=useState<InvitationDetails|null>(null);const [invitationCsrf,setInvitationCsrf]=useState("");
  const [error,setError]=useState<string|null>(null);const [loading,setLoading]=useState(true);
  const [privacyMessage,setPrivacyMessage]=useState<string|null>(null);
  useEffect(()=>{let active=true;async function load(){let invitationFlow=false;try{const fragment=new URLSearchParams(window.location.hash.slice(1));const interviewToken=fragment.get("invitation");const token=fragment.get("token");
    const deletion=fragment.get("deletion");
    if(deletion){window.history.replaceState(null,"",window.location.pathname+window.location.search);await confirmCandidatePrivacyDeletion(deletion);if(active)setPrivacyMessage(t("deletionConfirmed"));}
    else if(interviewToken){invitationFlow=true;window.history.replaceState(null,"",window.location.pathname+window.location.search);const session=await exchangeInterviewInvitation(interviewToken);if(active){setInvitationCsrf(session.csrfToken);setInvitation(session.invitation);}}
    else if(token){const session=await exchangeCandidateToken(token);window.history.replaceState(null,"",window.location.pathname+window.location.search);if(active){setApplication(session.application);setCsrf(session.csrfToken);}}
    else{try{const current=await getCandidateApplication();if(active)setApplication(current);}catch{const session=await refreshCandidateSession();if(active){setApplication(session.application);setCsrf(session.csrfToken);}}}}
    catch(e){if(active)setError(invitationFlow&&e instanceof Error&&e.message.includes("INVITATION_EXPIRED")?t("invitation.expired"):t("invalid"));}finally{if(active)setLoading(false);}}load();return()=>{active=false};},[t]);
  async function withdraw(){if(!csrf){try{const session=await refreshCandidateSession();setCsrf(session.csrfToken);setApplication(await withdrawCandidateApplication(session.csrfToken));}catch{setError(t("invalid"));}return;}try{setApplication(await withdrawCandidateApplication(csrf));}catch{setError(t("failed"));}}
  async function logout(){try{if(csrf)await closeCandidateSession(csrf);}finally{setApplication(null);setCsrf("");}}
  async function requestDeletion(){let value=csrf;try{if(!value){const session=await refreshCandidateSession();value=session.csrfToken;setCsrf(value);}await requestCandidatePrivacyDeletion(value);setPrivacyMessage(t("deletionRequested"));}catch{setError(t("failed"));}}
  if(loading)return <p role="status" className="text-slate-600">{t("loading")}</p>;
  if(error)return <div role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-amber-900">{error}</div>;
  if(privacyMessage&&!application)return <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 p-6 text-emerald-950">{privacyMessage}</div>;
  if(invitation&&invitationCsrf)return <InvitationScheduler csrf={invitationCsrf} initial={invitation}/>;
  if(!application)return <div role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-amber-900">{t("invalid")}</div>;
  return <section className="rounded-xl border bg-white p-6 shadow-sm"><p className="text-sm font-medium text-indigo-600">{application.companyName}</p><h1 className="mt-1 text-2xl font-bold">{application.jobTitle}</h1>{privacyMessage&&<p role="status" className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-950">{privacyMessage}</p>}<dl className="mt-6 grid gap-4 text-sm sm:grid-cols-2"><div><dt className="text-slate-500">{t("status")}</dt><dd className="font-medium">{t(`statuses.${application.status}`)}</dd></div><div><dt className="text-slate-500">{t("submitted")}</dt><dd>{new Intl.DateTimeFormat(undefined,{dateStyle:"medium"}).format(new Date(application.submittedAt))}</dd></div><div><dt className="text-slate-500">{t("cv")}</dt><dd>{application.cvPresent?t("cvPresent"):t("cvAbsent")}</dd></div></dl><div className="mt-8 flex flex-wrap gap-3">{application.status!=="WITHDRAWN"&&<Button variant="destructive" onClick={withdraw}>{t("withdraw")}</Button>}<Button variant="destructive" onClick={requestDeletion}>{t("deleteData")}</Button><Button variant="outline" onClick={logout}>{t("closeSession")}</Button></div></section>;
}

function InvitationScheduler({csrf,initial}:{csrf:string;initial:InvitationDetails}){
  const t=useTranslations("Jobs.manage.invitation");const [details,setDetails]=useState(initial);const [slots,setSlots]=useState<InterviewSlot[]>([]);
  const [nextFrom,setNextFrom]=useState<string|null>(null);const [showSlots,setShowSlots]=useState(initial.status==="INVITED");
  const [busy,setBusy]=useState(false);const [error,setError]=useState<string|null>(null);
  useEffect(()=>{if(!showSlots)return;let active=true;getInterviewSlots().then(page=>{if(active){setSlots(page.items);setNextFrom(page.nextFrom);}}).catch(e=>active&&setError(t(messageKey(e))));return()=>{active=false};},[showSlots,t]);
  const groups=useMemo(()=>{const format=new Intl.DateTimeFormat(undefined,{dateStyle:"full"});return slots.reduce<Record<string,InterviewSlot[]>>((all,slot)=>{const key=format.format(new Date(slot.startAt));(all[key]??=[]).push(slot);return all;},{});},[slots]);
  async function choose(slot:InterviewSlot){if(!window.confirm(t(details.status==="SCHEDULED"?"confirmReschedule":"confirmSchedule")))return;setBusy(true);setError(null);try{const updated=await scheduleInterview(csrf,slot.startAt,details.status==="SCHEDULED");setDetails(updated);setShowSlots(false);setSlots([]);}catch(e){setError(t(messageKey(e)));}finally{setBusy(false);}}
  async function more(){if(!nextFrom)return;setBusy(true);try{const page=await getInterviewSlots(nextFrom);setSlots(current=>[...current,...page.items]);setNextFrom(page.nextFrom);}catch(e){setError(t(messageKey(e)));}finally{setBusy(false);}}
  async function withdraw(){if(!window.confirm(t("confirmWithdraw")))return;setBusy(true);try{setDetails(await withdrawInterviewInvitation(csrf));setShowSlots(false);}catch(e){setError(t(messageKey(e)));}finally{setBusy(false);}}
  const time=(value:string)=>new Intl.DateTimeFormat(undefined,{dateStyle:"medium",timeStyle:"short",timeZone:Intl.DateTimeFormat().resolvedOptions().timeZone}).format(new Date(value));
  return <section className="rounded-xl border bg-white p-6 shadow-sm"><p className="text-sm font-medium text-indigo-600">{details.companyName}</p><h1 className="mt-1 text-2xl font-bold">{details.jobTitle}</h1><p className="mt-2 text-slate-600">{t("hello",{name:details.candidateName})}</p>
    {details.scheduledStartAt&&details.status==="SCHEDULED"&&<div className="mt-6 rounded-lg bg-emerald-50 p-4 text-emerald-950"><p className="font-medium">{t("scheduled")}</p><p>{time(details.scheduledStartAt)}</p><p className="mt-1 text-xs">{t("browserTimezone",{timezone:Intl.DateTimeFormat().resolvedOptions().timeZone})}</p></div>}
    {details.status==="CANCELLED"&&<p className="mt-6 rounded-lg bg-slate-100 p-4">{t("withdrawn")}</p>}
    {showSlots&&<div className="mt-8"><h2 className="text-lg font-semibold">{t("chooseSlot")}</h2>{Object.keys(groups).length===0&&!busy&&<p className="mt-3 rounded-lg bg-amber-50 p-4 text-amber-900">{t("empty")}</p>}{Object.entries(groups).map(([date,items])=><div key={date} className="mt-5"><h3 className="font-medium">{date}</h3><div className="mt-2 flex flex-wrap gap-2">{items.map(slot=><Button key={slot.startAt} variant="outline" disabled={busy} onClick={()=>choose(slot)}>{new Intl.DateTimeFormat(undefined,{timeStyle:"short"}).format(new Date(slot.startAt))}</Button>)}</div></div>)}{nextFrom&&<Button className="mt-6" variant="outline" disabled={busy} onClick={more}>{t("loadMore")}</Button>}</div>}
    {error&&<p role="alert" className="mt-5 rounded-lg bg-red-50 p-4 text-red-700">{error}</p>}
    <div className="mt-8 flex flex-wrap gap-3">{details.status==="SCHEDULED"&&!showSlots&&<Button variant="outline" onClick={()=>setShowSlots(true)}>{t("reschedule")}</Button>}{details.status!=="CANCELLED"&&<Button variant="destructive" disabled={busy} onClick={withdraw}>{t("withdraw")}</Button>}</div>
  </section>;
}

function messageKey(error:unknown):"slotUnavailable"|"quota"|"cutoff"|"failed"{const value=error instanceof Error?error.message:"";if(value.includes("SLOT_UNAVAILABLE"))return "slotUnavailable";if(value.includes("INTERVIEW_QUOTA_EXHAUSTED"))return "quota";if(value.includes("RESCHEDULE_CUTOFF"))return "cutoff";return "failed";}
