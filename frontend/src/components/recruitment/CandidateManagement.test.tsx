import { StrictMode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CandidateManagement, consumeCandidateAccessParameters, InvitationScheduler, messageKey } from "./CandidateManagement";

const api = vi.hoisted(() => ({
  exchangeCandidateToken: vi.fn(),
  exchangeInterviewInvitation: vi.fn(),
  getCandidateApplication: vi.fn(),
  getInterviewSlots: vi.fn(),
  refreshCandidateSession: vi.fn(),
  scheduleInterview: vi.fn(),
  withdrawInterviewInvitation: vi.fn(),
}));

vi.mock("next-intl", () => ({ useTranslations: () => (key: string) => key }));
vi.mock("@/lib/recruitment-api", () => ({
  exchangeCandidateToken: api.exchangeCandidateToken,
  exchangeInterviewInvitation: api.exchangeInterviewInvitation,
  getCandidateApplication: api.getCandidateApplication,
  getInterviewSlots: api.getInterviewSlots,
  refreshCandidateSession: api.refreshCandidateSession,
  scheduleInterview: api.scheduleInterview,
  withdrawCandidateApplication: vi.fn(),
  withdrawInterviewInvitation: api.withdrawInterviewInvitation,
  requestCandidatePrivacyDeletion: vi.fn(),
  confirmCandidatePrivacyDeletion: vi.fn(),
}));

describe("CandidateManagement verification exchange", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState(null, "", "/en/applications/manage?token=verification-token");
    api.exchangeCandidateToken.mockResolvedValue({
      csrfToken: "csrf",
      application: {
        applicationId: "application-id",
        jobPublicId: "job-id",
        companyName: "Acme",
        jobTitle: "Software Engineer",
        status: "SUBMITTED",
        submittedAt: "2026-07-26T10:00:00Z",
        verifiedAt: "2026-07-26T10:05:00Z",
        withdrawnAt: null,
        cvPresent: false,
      },
    });
  });

  it("exchanges a one-time token only once under React Strict Mode", async () => {
    render(<StrictMode><CandidateManagement /></StrictMode>);

    expect(await screen.findByText("Software Engineer")).toBeInTheDocument();
    await waitFor(() => expect(api.exchangeCandidateToken).toHaveBeenCalledOnce());
    expect(api.exchangeCandidateToken).toHaveBeenCalledWith("verification-token");
    expect(api.getCandidateApplication).not.toHaveBeenCalled();
    expect(api.refreshCandidateSession).not.toHaveBeenCalled();
    expect(window.location.search).toBe("");
    expect(screen.queryByText("invalid")).not.toBeInTheDocument();
  });

  it("continues to accept legacy fragment links", () => {
    window.history.replaceState(null,"","/en/applications/manage?source=email#token=legacy-token");
    expect(consumeCandidateAccessParameters()).toEqual({invitation:null,token:"legacy-token",deletion:null});
    expect(window.location.search).toBe("?source=email");
    expect(window.location.hash).toBe("");
  });

});

describe("candidate interview scheduling errors", () => {
  it("explains when the hiring team has not configured availability", () => {
    expect(messageKey(new Error("INTERVIEW_AVAILABILITY_NOT_CONFIGURED"))).toBe("availability");
  });

  it("shows dates first and only the times for the selected date", async () => {
    const mondayMorning="2026-07-27T02:00:00Z";
    const mondayLater="2026-07-27T03:00:00Z";
    const tuesdayAfternoon="2026-07-28T07:00:00Z";
    api.getInterviewSlots.mockResolvedValue({items:[
      {startAt:mondayMorning,endAt:"2026-07-27T02:30:00Z",schedulingTimezone:"Asia/Ho_Chi_Minh"},
      {startAt:mondayLater,endAt:"2026-07-27T03:30:00Z",schedulingTimezone:"Asia/Ho_Chi_Minh"},
      {startAt:tuesdayAfternoon,endAt:"2026-07-28T07:30:00Z",schedulingTimezone:"Asia/Ho_Chi_Minh"},
    ],nextFrom:null,schedulingTimezone:"Asia/Ho_Chi_Minh"});
    render(<InvitationScheduler csrf="csrf" initial={{interviewId:"interview",companyName:"Acme",jobTitle:"Engineer",candidateName:"Candidate",status:"INVITED",scheduledStartAt:null,scheduledEndAt:null,schedulingTimezone:null,invitationExpiresAt:"2026-08-02T00:00:00Z",rescheduleCount:0}}/>);

    const mondayLabel=new Intl.DateTimeFormat(undefined,{day:"numeric",month:"short"}).format(new Date(mondayMorning));
    const tuesdayLabel=new Intl.DateTimeFormat(undefined,{day:"numeric",month:"short"}).format(new Date(tuesdayAfternoon));
    const mondayTime=new Intl.DateTimeFormat(undefined,{timeStyle:"short"}).format(new Date(mondayMorning));
    const tuesdayTime=new Intl.DateTimeFormat(undefined,{timeStyle:"short"}).format(new Date(tuesdayAfternoon));
    expect(await screen.findByRole("button",{name:new RegExp(mondayLabel)})).toHaveAttribute("aria-pressed","true");
    expect(screen.getByRole("button",{name:mondayTime})).toBeInTheDocument();
    expect(screen.queryByRole("button",{name:tuesdayTime})).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button",{name:new RegExp(tuesdayLabel)}));
    expect(screen.getByRole("button",{name:tuesdayTime})).toBeInTheDocument();
    expect(screen.queryByRole("button",{name:mondayTime})).not.toBeInTheDocument();
  });
});
