import {describe,expect,it} from "vitest";
import {cvAiDisclosureKey,requiresCvAiConsent} from "./ApplicationForm";

describe("public CV AI disclosure",()=>{
  const file=new File(["cv"],"cv.pdf",{type:"application/pdf"});
  it("requires consent only for an attached CV in an enabled mode",()=>{
    expect(requiresCvAiConsent({cvAiMode:"SUMMARY_ONLY"},null)).toBe(false);
    expect(requiresCvAiConsent({cvAiMode:"OFF"},file)).toBe(false);
    expect(requiresCvAiConsent({cvAiMode:"SUMMARY_ONLY"},file)).toBe(true);
    expect(requiresCvAiConsent({cvAiMode:"PERSONALIZED_QUESTIONS"},file)).toBe(true);
  });
  it("selects mode-specific bilingual message keys",()=>{
    expect(cvAiDisclosureKey("SUMMARY_ONLY")).toBe("cvAiSummaryDisclosure");
    expect(cvAiDisclosureKey("PERSONALIZED_QUESTIONS")).toBe("cvAiPersonalizedDisclosure");
  });
});
