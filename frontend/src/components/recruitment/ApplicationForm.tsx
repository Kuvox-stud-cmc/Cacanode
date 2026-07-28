"use client";

import { useEffect, useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useLocale, useTranslations } from "next-intl";
import { submitApplication, type PublicJob } from "@/lib/recruitment-api";
import { publicConfig } from "@/lib/public-config";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useLocaleChangeDraft } from "@/hooks/useLocaleChangeDraft";
import { CountryPhoneInput } from "@/components/recruitment/CountryPhoneInput";
import { SUPPORTED_PHONE_COUNTRIES, toE164, type SupportedPhoneCountry } from "@/lib/recruitment-phone";

const phoneCountries=SUPPORTED_PHONE_COUNTRIES.map((option)=>option.region) as [SupportedPhoneCountry,...SupportedPhoneCountry[]];
const schema=z.object({fullName:z.string().min(1).max(200),email:z.string().email().max(320),phoneCountry:z.enum(phoneCountries),phoneNational:z.string(),privacyConsent:z.boolean().refine(Boolean),cvUseConsent:z.boolean()}).superRefine((value,context)=>{if(!toE164(value.phoneCountry,value.phoneNational))context.addIssue({code:"custom",path:["phoneNational"],message:"Invalid phone number"});});
type FormValues=z.infer<typeof schema>;

export function requiresCvAiConsent(job:Pick<PublicJob,"cvAiMode">,file:File|null){return file!==null&&job.cvAiMode!=="OFF";}
export function cvAiDisclosureKey(mode:PublicJob["cvAiMode"]){return mode==="PERSONALIZED_QUESTIONS"?"cvAiPersonalizedDisclosure":"cvAiSummaryDisclosure";}

export function ApplicationForm({job}:{job:PublicJob}){
  const t=useTranslations("Jobs.apply");const locale=useLocale();
  const {register,handleSubmit,reset,control,setValue,formState:{errors,isSubmitting}}=useForm<FormValues>({resolver:zodResolver(schema),defaultValues:{fullName:"",email:"",phoneCountry:"VN",phoneNational:"",privacyConsent:false,cvUseConsent:false}});
  const [file,setFile]=useState<File|null>(null);const [fileError,setFileError]=useState<string|null>(null);
  const [accepted,setAccepted]=useState(false);const [error,setError]=useState<string|null>(null);const [turnstile,setTurnstile]=useState("");
  const [screeningAnswers,setScreeningAnswers]=useState<Record<string,string>>({});
  const values=useWatch({control});
  const phoneCountry=(values.phoneCountry??"VN") as SupportedPhoneCountry;const phoneNational=values.phoneNational??"";
  const clearLocaleDraft=useLocaleChangeDraft(`recruitment:public-application:${job.publicId}`,{values,screeningAnswers},(draft)=>{reset({...draft.values,phoneCountry:draft.values.phoneCountry??"VN",phoneNational:draft.values.phoneNational??""});setScreeningAnswers(draft.screeningAnswers);});
  const widget=useRef<HTMLDivElement>(null);
  useEffect(()=>{if(!publicConfig.turnstileSiteKey||!widget.current)return;const render=()=>window.turnstile?.render(widget.current!,{sitekey:publicConfig.turnstileSiteKey,callback:setTurnstile,"expired-callback":()=>setTurnstile("")});if(window.turnstile){render();return;}const script=document.createElement("script");script.src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";script.async=true;script.onload=render;document.head.appendChild(script);},[]);
  function choose(next:File|null){setFileError(null);if(!next){setFile(null);return;}const lower=next.name.toLowerCase();const validType=next.type==="application/pdf"||next.type==="application/vnd.openxmlformats-officedocument.wordprocessingml.document";if(next.size>5*1024*1024||(!lower.endsWith(".pdf")&&!lower.endsWith(".docx"))||!validType){setFileError(t("invalidFile"));setFile(null);return;}setFile(next);}
  async function submit(values:FormValues){if(job.cvPolicy==="REQUIRED"&&!file){setFileError(t("requiredFile"));return;}if(requiresCvAiConsent(job,file)&&!values.cvUseConsent){setError(t("cvConsentRequired"));return;}if(job.screeningQuestions.some(q=>!screeningAnswers[q.questionId])){setError(t("screeningRequired"));return;}const phone=toE164(values.phoneCountry,values.phoneNational);if(!phone){setError(t("invalidPhone"));return;}const application={fullName:values.fullName,email:values.email,phone,privacyConsent:values.privacyConsent,cvUseConsent:values.cvUseConsent};setError(null);try{await submitApplication(job.publicId,{...application,locale:locale==="vi"?"vi-VN":"en-US",screeningAnswers:job.screeningQuestions.map(q=>({questionId:q.questionId,optionId:screeningAnswers[q.questionId]}))},file,turnstile);clearLocaleDraft();setAccepted(true);}catch(e){setError(e instanceof Error?e.message:t("failed"));}}
  if(accepted)return <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 p-8"><h2 className="text-xl font-semibold text-emerald-950">{t("acceptedTitle")}</h2><p className="mt-2 text-emerald-800">{t("acceptedDescription")}</p></div>;
  return <form onSubmit={handleSubmit(submit)} className="space-y-5 rounded-xl border bg-white p-5 shadow-sm sm:p-7" noValidate>
    <div><Label htmlFor="fullName">{t("fullName")}</Label><Input id="fullName" {...register("fullName")} aria-invalid={!!errors.fullName}/></div>
    <div><Label htmlFor="email">{t("email")}</Label><Input id="email" type="email" {...register("email")} aria-invalid={!!errors.email}/></div>
    <CountryPhoneInput country={phoneCountry} nationalNumber={phoneNational} locale={locale} countryLabel={t("phoneCountry")} numberLabel={t("phone")} help={t("phoneHelp")} invalidMessage={t("invalidPhone")} invalid={!!errors.phoneNational} disabled={isSubmitting} onCountryChange={(country)=>setValue("phoneCountry",country,{shouldDirty:true,shouldValidate:true})} onNationalNumberChange={(value)=>setValue("phoneNational",value,{shouldDirty:true,shouldValidate:true})}/>
    {job.cvPolicy!=="DISABLED"&&<div><Label htmlFor="cv">{t("cv")} {job.cvPolicy==="REQUIRED"?"*":""}</Label><Input id="cv" type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={e=>choose(e.target.files?.[0]??null)}/><p className="mt-1 text-xs text-slate-500">{t("cvHelp")}</p>{fileError&&<p role="alert" className="mt-1 text-sm text-red-600">{fileError}</p>}</div>}
    {job.screeningQuestions.map((question,index)=><fieldset key={question.questionId} className="space-y-2 rounded-lg border p-4"><legend className="px-1 font-medium">{index+1}. {question.prompt}</legend>{question.options.map(option=><label key={option.optionId} className="flex items-center gap-3 text-sm"><input type="radio" name={`screening-${question.questionId}`} value={option.optionId} checked={screeningAnswers[question.questionId]===option.optionId} onChange={()=>setScreeningAnswers(current=>({...current,[question.questionId]:option.optionId}))}/><span>{option.label}</span></label>)}</fieldset>)}
    <label className="flex items-start gap-3 text-sm"><input type="checkbox" className="mt-1" {...register("privacyConsent")}/><span>{t("privacyConsent")}</span></label>
    {job.cvAiMode!=="OFF"&&<div className="rounded-lg border border-indigo-100 bg-indigo-50 p-4 text-sm text-indigo-950" role="note"><p>{t(cvAiDisclosureKey(job.cvAiMode))}</p>{file&&<label className="mt-3 flex items-start gap-3"><input type="checkbox" className="mt-1" {...register("cvUseConsent")}/><span>{t("cvUseConsent")}</span></label>}</div>}
    <div ref={widget}/>{error&&<p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <Button type="submit" disabled={isSubmitting} className="w-full bg-indigo-600 text-white hover:bg-indigo-700">{isSubmitting?t("submitting"):t("submit")}</Button>
  </form>;
}
