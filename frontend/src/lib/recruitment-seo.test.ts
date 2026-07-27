import { describe, expect, it } from "vitest";
import { jobPostingJsonLd, publicJobMetadata } from "./recruitment-seo";
import type { PublicJob } from "./recruitment-api";

function job(discoverable:boolean):PublicJob {
  return{publicId:"public-1",tenantSlug:"acme",companyName:"Acme",title:"Engineer",description:"Plain search and SEO description",
    descriptionHtml:"<p><strong>Rich description</strong></p>",department:null,location:"Hanoi",employmentType:"FULL_TIME",
    workMode:"REMOTE",experienceLevel:"MID",language:"en-US",cvPolicy:"OPTIONAL",cvAiMode:"OFF",cvAiDisclosed:false,
    screeningQuestions:[],publishedAt:"2026-07-20T00:00:00",closingAt:"2026-08-20T00:00:00",discoverable};
}

describe("public job SEO",()=>{
  it("keeps canonical, alternates, OpenGraph, and plain-text JSON-LD for discoverable jobs",()=>{
    const value=job(true);const metadata=publicJobMetadata("vi",value);const jsonLd=jobPostingJsonLd(value);
    expect(metadata.alternates?.canonical).toBe("http://localhost:3000/vi/jobs/public-1");
    expect(metadata.openGraph).toMatchObject({description:value.description});
    expect(jsonLd).toMatchObject({"@type":"JobPosting",description:value.description,jobLocationType:"TELECOMMUTE"});
    expect(JSON.stringify(jsonLd)).not.toContain("Rich description");
  });

  it("marks unlisted jobs noindex/nofollow and omits JobPosting data",()=>{
    const value=job(false);const metadata=publicJobMetadata("en",value);
    expect(metadata.robots).toMatchObject({index:false,follow:false});
    expect(metadata.alternates).toBeUndefined();
    expect(jobPostingJsonLd(value)).toBeNull();
  });
});
