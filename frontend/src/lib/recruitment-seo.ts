import { publicConfig } from "@/lib/public-config";
import type { Metadata } from "next";
import type { PublicJob } from "@/lib/recruitment-api";

export function localizedPath(locale:string,path:string){return `${publicConfig.siteUrl.replace(/\/$/,"")}${locale==="vi"?"/vi":""}${path}`;}
export function languageAlternates(path:string){return {en:localizedPath("en",path),vi:localizedPath("vi",path),"x-default":localizedPath("en",path)};}
export function tenantCareersPath(tenantSlug:string){return `/careers/${encodeURIComponent(tenantSlug)}`;}

export function publicJobMetadata(locale:string,job:PublicJob):Metadata {
  const path=`/jobs/${job.publicId}`;
  const base={title:`${job.title} — ${job.companyName}`,description:job.description.slice(0,155)};
  if(!job.discoverable)return{...base,robots:{index:false,follow:false}};
  return{...base,alternates:{canonical:localizedPath(locale,path),languages:languageAlternates(path)},
    openGraph:{title:job.title,description:job.description.slice(0,155),type:"website",url:localizedPath(locale,path)}};
}

export function jobPostingJsonLd(job:PublicJob):Record<string,unknown>|null {
  if(!job.discoverable)return null;
  return{"@context":"https://schema.org","@type":"JobPosting",title:job.title,description:job.description,
    datePosted:job.publishedAt,validThrough:job.closingAt,employmentType:job.employmentType,
    hiringOrganization:{"@type":"Organization",name:job.companyName},
    jobLocation:job.location?{"@type":"Place",address:{"@type":"PostalAddress",addressLocality:job.location}}:undefined,
    jobLocationType:job.workMode==="REMOTE"?"TELECOMMUTE":undefined};
}
