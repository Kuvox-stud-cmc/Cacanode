import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CandidateDetailPage } from "./CandidateDetailPage";
import { CandidatesListPage } from "./CandidatesListPage";

const api = vi.hoisted(() => ({
  request: vi.fn(),
  getRecruitmentCandidate: vi.fn(),
  listRecruitmentApplications: vi.fn(),
  listRecruitmentCandidates: vi.fn(),
}));

vi.mock("next-intl", () => {
  const candidateLabels: Record<string,string> = {
    email: "Email", phone: "Số điện thoại", added: "Ngày thêm", notes: "Ghi chú", details: "Chi tiết",
    editCandidate: "Chỉnh sửa {name}", deleteCandidate: "Xóa {name}", back: "Quay lại danh sách ứng viên",
    candidateId: "Mã ứng viên", editProfile: "Chỉnh sửa hồ sơ", delete: "Xóa", candidateInfo: "Thông tin ứng viên",
    fullName: "Họ và tên", emailAddress: "Địa chỉ email", phoneNumber: "Số điện thoại", createdAt: "Ngày tạo",
    recruiterNotes: "Ghi chú của nhà tuyển dụng", applicationHistory: "Lịch sử ứng tuyển ({count})",
    viewApplications: "Xem hồ sơ ứng tuyển", noApplications: "Ứng viên này chưa nộp hồ sơ ứng tuyển nào.", view: "Xem",
  };
  const recruitmentLabels: Record<string,string> = {
    "nav.candidates": "Ứng viên", "pages.candidates": "Quản lý ứng viên", "actions.createCandidate": "Tạo ứng viên",
    search: "Tìm kiếm", loading: "Đang tải", empty: "Không có dữ liệu", previous: "Trước", next: "Sau",
    pagination: "Trang {page}/{pages} · {total} bản ghi", "dialogs.deleteCandidateTitle": "Xóa ứng viên",
    "dialogs.deleteCandidate": "Xóa hồ sơ ứng viên?", "forms.delete": "Xóa", "actions.saving": "Đang lưu", save: "Lưu",
  };
  const translate = (labels:Record<string,string>) => (key:string,values?:Record<string,unknown>) => {
    let value=labels[key]??key;
    for(const [name,replacement] of Object.entries(values??{}))value=value.replace(`{${name}}`,String(replacement));
    return value;
  };
  return {
    useLocale: () => "vi",
    useFormatter: () => ({dateTime: () => "26/07/2026"}),
    useTranslations: (namespace:string) => namespace==="Recruitment.candidatePages"?translate(candidateLabels):translate(recruitmentLabels),
  };
});
vi.mock("next/navigation", () => ({useSearchParams: () => new URLSearchParams()}));
vi.mock("@/i18n/navigation", () => ({
  Link: ({children,href}:{children:React.ReactNode;href:string}) => <a href={href}>{children}</a>,
  useRouter: () => ({push:vi.fn(),replace:vi.fn()}),
}));
vi.mock("@/hooks/useApiClient", () => ({useApiClient: () => ({request:api.request})}));
vi.mock("@/components/providers/StoreProvider", () => ({useAuthStore: (selector:(state:{user:{tenantId:string}})=>unknown) => selector({user:{tenantId:"tenant-id"}})}));
vi.mock("@/hooks/useLocaleChangeDraft", () => ({useLocaleChangeDraft: () => vi.fn()}));
vi.mock("@/components/recruitment/useRecruitmentConfirmation", () => ({useRecruitmentConfirmation: () => ({confirm:vi.fn(),confirmationDialog:null})}));
vi.mock("@/lib/recruitment-admin-api", () => ({
  deleteRecruitmentCandidate:vi.fn(), getRecruitmentCandidate:api.getRecruitmentCandidate,
  listRecruitmentApplications:api.listRecruitmentApplications, listRecruitmentCandidates:api.listRecruitmentCandidates,
  saveCandidate:vi.fn(),
}));

const candidate={id:"candidate-id",fullName:"Nguyễn An",email:"an@example.com",phone:"+84901234567",notes:"Có kinh nghiệm",createdAt:"2026-07-26T10:00:00Z",updatedAt:"2026-07-26T10:00:00Z"};

describe("Vietnamese recruitment candidate pages", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getRecruitmentCandidate.mockResolvedValue(candidate);
    api.listRecruitmentCandidates.mockResolvedValue({items:[candidate],total:1});
    api.listRecruitmentApplications.mockResolvedValue({items:[{
      id:"application-id",jobTitle:"Kỹ sư phần mềm",status:"SUBMITTED_UNVERIFIED",submittedAt:"2026-07-26T10:00:00Z",
    }],total:1});
  });

  it("localizes the candidate list and its actions", async () => {
    render(<CandidatesListPage/>);
    expect(await screen.findByText("Nguyễn An")).toBeInTheDocument();
    expect(screen.getByText(/Số điện thoại:/)).toBeInTheDocument();
    expect(screen.getByText(/Ngày thêm:/)).toBeInTheDocument();
    expect(screen.getByRole("link",{name:/Chi tiết/})).toBeInTheDocument();
    expect(screen.getByRole("button",{name:"Chỉnh sửa Nguyễn An"})).toBeInTheDocument();
    expect(screen.queryByText("Details")).not.toBeInTheDocument();
  });

  it("localizes candidate details and friendly application statuses", async () => {
    render(<CandidateDetailPage candidateId="candidate-id"/>);
    expect(await screen.findByText("Thông tin ứng viên")).toBeInTheDocument();
    expect(screen.getByText("Quay lại danh sách ứng viên")).toBeInTheDocument();
    expect(screen.getByText("Lịch sử ứng tuyển (1)")).toBeInTheDocument();
    expect(screen.getByText("Chờ xác minh email")).toBeInTheDocument();
    expect(screen.getByRole("link",{name:"Xem hồ sơ ứng tuyển"})).toBeInTheDocument();
    expect(screen.queryByText("Candidate Info")).not.toBeInTheDocument();
  });
});
