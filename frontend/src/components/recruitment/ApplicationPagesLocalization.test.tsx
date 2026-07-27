import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApplicationDetailPage, cvAnalysisStateKey } from "./ApplicationDetailPage";
import { ApplicationsListPage } from "./ApplicationsListPage";

const api = vi.hoisted(() => ({
  request: vi.fn(),
  getApplicationDetail: vi.fn(),
  getCvAnalysis: vi.fn(),
  listRecruitmentApplications: vi.fn(),
}));

vi.mock("next-intl", () => {
  const applicationLabels: Record<string, string> = {
    createApplication: "Tạo hồ sơ ứng tuyển", cvAttached: "Có CV đính kèm", job: "Công việc", email: "Email", submitted: "Ngày nộp", viewDetail: "Xem chi tiết",
    candidateProfile: "Thông tin ứng viên", fullName: "Họ và tên", phone: "Số điện thoại", lifecycle: "Vòng đời và trạng thái", applicationStatus: "Trạng thái hồ sơ", submittedAt: "Thời điểm nộp",
    cvTitle: "Sơ yếu lý lịch (CV)", noCv: "Hồ sơ ứng tuyển này không có tệp CV.", screeningAnswers: "Câu trả lời sàng lọc ({count})", cvAiReview: "Đánh giá CV bằng AI",
    screeningEvidence: "Bằng chứng sàng lọc", screeningDescription: "Xem câu trả lời của ứng viên cho các câu hỏi sàng lọc công việc.", noScreening: "Công việc này không yêu cầu câu hỏi sàng lọc.",
    back: "Quay lại danh sách hồ sơ", applyingFor: "Ứng tuyển vị trí {job}",
  };
  const recruitmentLabels = {
      "nav.applications": "Hồ sơ",
      "pages.applications": "Xem ứng viên và bằng chứng sàng lọc.",
      search: "Tìm kiếm",
      allStatuses: "Mọi trạng thái",
      loading: "Đang tải",
      empty: "Không có dữ liệu",
      advisory: "Kết quả AI chỉ mang tính tham khảo.",
      "actions.invite": "Gửi lời mời",
      "forms.delete": "Xóa",
  } as Record<string, string>;
  const translateApplication = (key: string, values?: Record<string, unknown>) => {
    let value = applicationLabels[key] ?? key;
    for (const [name, replacement] of Object.entries(values ?? {})) value = value?.replace(`{${name}}`, String(replacement));
    return value;
  };
  const translateRecruitment = (key: string) => recruitmentLabels[key] ?? key;
  return {
    useLocale: () => "vi",
    useFormatter: () => ({ number: String, dateTime: () => "26/07/2026" }),
    useTranslations: (namespace: string) => namespace === "Recruitment.applicationPages" ? translateApplication : translateRecruitment,
  };
});
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams() }));
vi.mock("@/i18n/navigation", () => ({
  Link: ({ children, href }: { children: React.ReactNode; href: string }) => <a href={href}>{children}</a>,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));
vi.mock("@/hooks/useApiClient", () => ({ useApiClient: () => ({ request: api.request }) }));
vi.mock("@/lib/recruitment-admin-api", () => ({
  getApplicationDetail: api.getApplicationDetail,
  getCvAnalysis: api.getCvAnalysis,
  listRecruitmentApplications: api.listRecruitmentApplications,
  listRecruitmentCandidates: vi.fn(),
  listRecruitmentJobs: vi.fn(),
  createRecruitmentApplication: vi.fn(),
  sendApplicationCompletionLink: vi.fn(),
  deleteRecruitmentCv: vi.fn(),
  inviteApplication: vi.fn(),
  transitionApplication: vi.fn(),
  deleteRecruitmentApplication: vi.fn(),
  cvUrl: () => "/cv",
}));

const application = {
  id: "app-id", jobId: "job-id", jobTitle: "Kỹ sư phần mềm", candidateId: "candidate-id",
  candidateName: "Nguyễn An", candidateEmail: "an@example.com", status: "SUBMITTED",
  submittedAt: "2026-07-26T10:00:00Z", verifiedAt: "2026-07-26T10:05:00Z", withdrawnAt: null,
  cvPresent: true, cvAnalysisStatus: "NOT_REQUESTED", overallScore: null, englishBand: null,
  interviewStatus: null, updatedAt: "2026-07-26T10:05:00Z",
};

describe("Vietnamese recruitment application pages", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.listRecruitmentApplications.mockResolvedValue({ items: [application], total: 1 });
    api.getApplicationDetail.mockResolvedValue({
      application: { ...application, cvPresent: false },
      candidate: { id: "candidate-id", fullName: "Nguyễn An", email: "an@example.com", phone: "+84901234567", notes: null, createdAt: "", updatedAt: "" },
      screeningQuestions: [], screeningAnswers: [],
    });
    api.getCvAnalysis.mockRejectedValue(new Error("not available"));
  });

  it("localizes the applications list", async () => {
    render(<ApplicationsListPage />);
    expect(await screen.findByText("Nguyễn An")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Tạo hồ sơ ứng tuyển/ })).toBeInTheDocument();
    expect(screen.getByText("Có CV đính kèm")).toBeInTheDocument();
    expect(screen.getByText(/Công việc:/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Xem chi tiết/ })).toBeInTheDocument();
  });

  it("localizes the application detail content", async () => {
    render(<ApplicationDetailPage applicationId="app-id" />);
    expect(await screen.findByText("Thông tin ứng viên")).toBeInTheDocument();
    expect(screen.getByText("Vòng đời và trạng thái")).toBeInTheDocument();
    expect(screen.getByText("Sơ yếu lý lịch (CV)")).toBeInTheDocument();
    expect(screen.getByText("Bằng chứng sàng lọc")).toBeInTheDocument();
    expect(screen.queryByText("Candidate Profile")).not.toBeInTheDocument();
  });

  it("explains every empty CV AI state instead of rendering a blank tab", () => {
    expect(cvAnalysisStateKey({cvPresent:true,applicationStatus:"SUBMITTED_UNVERIFIED",analysisStatus:"NOT_REQUESTED",mode:"PERSONALIZED_QUESTIONS"})).toBe("cvAwaitingVerification");
    expect(cvAnalysisStateKey({cvPresent:true,applicationStatus:"SUBMITTED",analysisStatus:"PENDING",mode:"SUMMARY_ONLY"})).toBe("aiPending");
    expect(cvAnalysisStateKey({cvPresent:true,applicationStatus:"SUBMITTED",analysisStatus:"SKIPPED_QUOTA",mode:"SUMMARY_ONLY"})).toBe("cvQuota");
    expect(cvAnalysisStateKey({cvPresent:true,applicationStatus:"SUBMITTED",analysisStatus:"NOT_REQUESTED",mode:"OFF"})).toBe("cvDisabled");
  });
});
