import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { InterviewDetailPage } from "./InterviewDetailPage";
import { InterviewsListPage } from "./InterviewsListPage";

const api=vi.hoisted(()=>({
  request:vi.fn(),listRecruitmentInterviews:vi.fn(),getRecruitmentInterview:vi.fn(),
  getInterviewAttempts:vi.fn(),getInterviewDeliveryHistory:vi.fn(),getInterviewRecordings:vi.fn(),
  getInterviewTranscript:vi.fn(),getInterviewResult:vi.fn(),getDialEligibility:vi.fn(),getRecordingBlob:vi.fn(),
}));

vi.mock("next-intl",()=>{
  const labels:Record<string,string>={
    score:"Điểm",job:"Công việc",scheduled:"Đã lên lịch",notScheduled:"Chưa lên lịch",detailsAudio:"Chi tiết và ghi âm",
    back:"Quay lại danh sách phỏng vấn",applicationId:"Mã hồ sơ ứng tuyển",overview:"Tổng quan",attempts:"Lần gọi ({count})",
    delivery:"Gửi thông báo ({count})",transcript:"Bản ghi lời thoại",resultReport:"Báo cáo kết quả",recordings:"Ghi âm ({count})",
    scheduleInformation:"Thông tin lịch",scheduledStart:"Thời gian bắt đầu",scheduledEnd:"Thời gian kết thúc",timezone:"Múi giờ",
    rescheduleCount:"Số lần đổi lịch",callExecution:"Thực hiện cuộc gọi",startedAt:"Bắt đầu lúc",completedAt:"Hoàn tất lúc",
    englishBand:"Mức năng lực tiếng Anh",attemptHistory:"Lịch sử lần gọi",noAttempts:"Chưa ghi nhận lần gọi nào.",
    deliveryHistory:"Lịch sử gửi email",noDelivery:"Chưa có bản ghi gửi thông báo.",interviewTranscript:"Bản ghi lời thoại phỏng vấn",
    noTranscript:"Buổi phỏng vấn này chưa có bản ghi lời thoại.",aiInterviewer:"Người phỏng vấn AI",candidate:"Ứng viên",
    evaluationReport:"Báo cáo đánh giá và chấm điểm bằng AI",noEvaluation:"Chưa có kết quả đánh giá.",
    overallScore:"Tổng điểm",englishProficiency:"Năng lực tiếng Anh",terminalKind:"Loại kết thúc",deliveryStatus:"Trạng thái tiếp nhận",
    notScored:"Chưa chấm điểm",notAssessed:"Không được đánh giá trong buổi phỏng vấn này",sectionBreakdown:"Chi tiết từng phần",
    kind:"Loại",questionId:"Mã câu hỏi",audioRecordings:"Bản ghi âm",noRecordings:"Không tạo được bản ghi âm.",
    recordingDisabled:"Tính năng ghi âm đã bị tắt cho buổi phỏng vấn này nên không có âm thanh nào được lưu.",
    recordingsDescription:"Nghe hoặc tải bản ghi âm cuộc gọi phỏng vấn.",recordingProcessing:"Bản ghi âm vẫn đang được xử lý.",
    recordingLoading:"Đang tải bản ghi âm…",recordingLoadFailed:"Không thể tải bản ghi âm.",recordingDownloadFailed:"Không thể tải bản ghi âm xuống.",
    recordingId:"Mã bản ghi",audioUnsupported:"Trình duyệt không hỗ trợ trình phát âm thanh.",retainedUntil:"Lưu giữ đến",indefinite:"Chưa lên lịch xóa",downloadMp3:"Tải MP3",
    developmentRedial:"Gọi lại (môi trường phát triển)",reinvite:"Gửi lời mời mới",
    failureReason:"Lý do thất bại",
    "callFailures.TWILIO_BUSY":"Nhà mạng của số đích báo máy bận trước khi điện thoại đổ chuông.",
    "callFailures.TWILIO_CALLBACK_ERROR":"Nhà cung cấp điện thoại không thể kết nối tới dịch vụ phỏng vấn.",
    "callFailures.CONSENT_AUDIO_ERROR":"Không thể hoàn tất lời hướng dẫn xác nhận đồng ý.",
    "callFailures.MEDIA_STREAM_ERROR":"Không thể thiết lập kết nối âm thanh trực tiếp cho buổi phỏng vấn.",
    "callFailures.VOICE_SERVICE_ERROR":"Dịch vụ giọng nói phỏng vấn đang tạm thời không khả dụng.",
    "callFailures.TECHNICAL_ERROR":"Cuộc gọi gặp sự cố kỹ thuật.",
  };
  const recruitment:Record<string,string>={"nav.interviews":"Phỏng vấn","pages.interviews":"Xem trạng thái phỏng vấn",search:"Tìm kiếm",allStatuses:"Tất cả trạng thái",loading:"Đang tải",empty:"Không có dữ liệu",pagination:"Trang {page}/{pages} · {total} bản ghi",previous:"Trước",next:"Sau"};
  const translate=(values:Record<string,string>)=>(key:string,args?:Record<string,unknown>)=>{let value=values[key]??key;for(const [name,replacement] of Object.entries(args??{}))value=value.replace(`{${name}}`,String(replacement));return value;};
  const interviewTranslations=translate(labels);const recruitmentTranslations=translate(recruitment);
  return {useLocale:()=>"vi",useFormatter:()=>({dateTime:()=>"26/07/2026"}),useTranslations:(namespace:string)=>namespace==="Recruitment.interviewPages"?interviewTranslations:recruitmentTranslations};
});
vi.mock("next/navigation",()=>({useSearchParams:()=>new URLSearchParams()}));
vi.mock("@/i18n/navigation",()=>({Link:({children,href}:{children:React.ReactNode;href:string})=><a href={href}>{children}</a>,useRouter:()=>({replace:vi.fn()})}));
vi.mock("@/hooks/useApiClient",()=>({useApiClient:()=>({request:api.request})}));
vi.mock("@/components/recruitment/useRecruitmentConfirmation",()=>({useRecruitmentConfirmation:()=>({confirm:vi.fn(),confirmationDialog:null})}));
vi.mock("@/lib/recruitment-admin-api",()=>({
  listRecruitmentInterviews:api.listRecruitmentInterviews,getRecruitmentInterview:api.getRecruitmentInterview,
  getInterviewAttempts:api.getInterviewAttempts,getInterviewDeliveryHistory:api.getInterviewDeliveryHistory,
  getInterviewRecordings:api.getInterviewRecordings,getInterviewTranscript:api.getInterviewTranscript,getInterviewResult:api.getInterviewResult,
  getDialEligibility:api.getDialEligibility,getRecordingBlob:api.getRecordingBlob,
  getInterviewSlotsAdmin:vi.fn(),cancelInterviewAdmin:vi.fn(),dialInterview:vi.fn(),reinviteInterview:vi.fn(),
  rescheduleInterviewAdmin:vi.fn(),scheduleInterviewAdmin:vi.fn(),
}));

const interview={id:"interview-id",applicationId:"application-id",jobId:"job-id",jobTitle:"Kỹ sư phần mềm",candidateId:"candidate-id",candidateName:"Nguyễn An",status:"COMPLETED",scheduledAt:"2026-07-26T10:00:00Z",scheduledStartAt:"2026-07-26T10:00:00Z",scheduledEndAt:"2026-07-26T10:30:00Z",schedulingTimezone:"Asia/Ho_Chi_Minh",rescheduleCount:0,startedAt:"2026-07-26T10:00:00Z",completedAt:"2026-07-26T10:30:00Z",overallScore:85,englishBand:"B2",recordingEnabled:false,recordingRetentionDays:0,updatedAt:"2026-07-26T10:30:00Z"};

describe("Vietnamese recruitment interview pages",()=>{
  beforeEach(()=>{
    vi.clearAllMocks();
    api.listRecruitmentInterviews.mockResolvedValue({items:[interview],total:1});
    api.getRecruitmentInterview.mockResolvedValue(interview);
    api.getInterviewAttempts.mockResolvedValue([]);api.getInterviewDeliveryHistory.mockResolvedValue([]);api.getInterviewRecordings.mockResolvedValue([]);
    api.getInterviewTranscript.mockResolvedValue(null);api.getInterviewResult.mockResolvedValue(null);
    api.getDialEligibility.mockResolvedValue(null);
    api.getRecordingBlob.mockResolvedValue(new Blob(["recording"],{type:"audio/mpeg"}));
    Object.defineProperty(URL,"createObjectURL",{configurable:true,writable:true,value:vi.fn(()=>"blob:recording")});
    Object.defineProperty(URL,"revokeObjectURL",{configurable:true,writable:true,value:vi.fn()});
  });

  it("localizes the interviews list",async()=>{
    render(<InterviewsListPage/>);
    expect(await screen.findByText("Nguyễn An")).toBeInTheDocument();
    expect(screen.getByText(/Công việc:/)).toBeInTheDocument();
    expect(screen.getByText(/Đã lên lịch:/)).toBeInTheDocument();
    expect(screen.getByRole("link",{name:/Chi tiết và ghi âm/})).toBeInTheDocument();
    expect(screen.queryByText("Details & Audio")).not.toBeInTheDocument();
  });

  it("localizes the interview detail tabs and empty states",async()=>{
    render(<InterviewDetailPage interviewId="interview-id"/>);
    expect(await screen.findByText("Thông tin lịch")).toBeInTheDocument();
    expect(screen.getByText("Quay lại danh sách phỏng vấn")).toBeInTheDocument();
    expect(screen.getByRole("tab",{name:"Bản ghi lời thoại"})).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab",{name:"Lần gọi (0)"}));
    expect(screen.getByText("Chưa ghi nhận lần gọi nào.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab",{name:"Ghi âm (0)"}));
    expect(screen.getByText("Tính năng ghi âm đã bị tắt cho buổi phỏng vấn này nên không có âm thanh nào được lưu.")).toBeInTheDocument();
    expect(screen.queryByText("Call Attempts History")).not.toBeInTheDocument();
  });

  it("polls processing recordings and loads ready audio through the authenticated client",async()=>{
    api.getRecruitmentInterview.mockResolvedValue({...interview,recordingEnabled:true,recordingRetentionDays:1});
    api.getInterviewRecordings
      .mockResolvedValueOnce([{recordingId:"recording-id",state:"COPY_PENDING",contentType:null,sizeBytes:null,retainedUntil:null,readyAt:null,deletedAt:null}])
      .mockResolvedValueOnce([{recordingId:"recording-id",state:"READY",contentType:"audio/mpeg",sizeBytes:9,retainedUntil:null,readyAt:"2026-07-27T05:40:17Z",deletedAt:null}]);
    const originalSetInterval=window.setInterval;
    window.setInterval=((handler:TimerHandler)=>{
      queueMicrotask(()=>{if(typeof handler==="function")handler();});
      return 1;
    }) as typeof window.setInterval;

    render(<InterviewDetailPage interviewId="interview-id"/>);
    fireEvent.click(await screen.findByRole("tab",{name:"Ghi âm (1)"}));

    await waitFor(()=>expect(api.getInterviewRecordings).toHaveBeenCalledTimes(2));
    await waitFor(()=>expect(api.getRecordingBlob).toHaveBeenCalledWith(api.request,"interview-id","recording-id"));
    expect(document.querySelector("audio")).toHaveAttribute("src","blob:recording");
    window.setInterval=originalSetInterval;
  });

  it("downloads ready recordings through the authenticated client",async()=>{
    api.getRecruitmentInterview.mockResolvedValue({...interview,recordingEnabled:true,recordingRetentionDays:1});
    api.getInterviewRecordings.mockResolvedValue([{recordingId:"recording-id",state:"READY",contentType:"audio/mpeg",sizeBytes:9,retainedUntil:null,readyAt:"2026-07-27T05:40:17Z",deletedAt:null}]);
    const click=vi.spyOn(HTMLAnchorElement.prototype,"click").mockImplementation(()=>undefined);

    render(<InterviewDetailPage interviewId="interview-id"/>);
    fireEvent.click(await screen.findByRole("tab",{name:"Ghi âm (1)"}));
    fireEvent.click(await screen.findByRole("button",{name:"Tải MP3"}));

    await waitFor(()=>expect(api.getRecordingBlob).toHaveBeenCalledWith(api.request,"interview-id","recording-id",true));
    expect(click).toHaveBeenCalled();
    click.mockRestore();
  });

  it("labels transcript speakers and score scales correctly",async()=>{
    api.getInterviewTranscript.mockResolvedValue({deliveryStatus:"COMPLETE",expectedTurnCount:2,persistedTurnCount:2,page:0,size:100,turns:[
      {turnId:"turn-ai",sequence:1,speaker:"INTERVIEWER",turnKind:"QUESTION",sectionId:"section",questionId:"question",languageTag:"en-US",transcript:"Tell me about your experience."},
      {turnId:"turn-candidate",sequence:2,speaker:"CANDIDATE",turnKind:"CANDIDATE_UTTERANCE",sectionId:"section",questionId:"question",languageTag:"en-US",transcript:"I have five years of experience."},
    ]});
    api.getInterviewResult.mockResolvedValue({terminalKind:"COMPLETED",deliveryStatus:"COMPLETE",completionReason:"FINISHED",failureCode:null,partial:false,overallScore:20,englishBand:null,advisoryOnly:true,englishWarning:"Advisory",sections:[{sectionId:"section",kind:"CORE",status:"COMPLETED",questions:[
      {questionId:"question",status:"COMPLETED",score:1,evidenceTurnIds:["turn-candidate"]},
      {questionId:"question-skipped",status:"SKIPPED",score:null,evidenceTurnIds:[]},
    ]}]});

    render(<InterviewDetailPage interviewId="interview-id"/>);
    await screen.findByText("Thông tin lịch");

    fireEvent.click(screen.getByRole("tab",{name:"Bản ghi lời thoại"}));
    expect(screen.getByText(/#1 · Người phỏng vấn AI/)).toBeInTheDocument();
    expect(screen.getByText(/#2 · Ứng viên/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab",{name:"Báo cáo kết quả"}));
    expect(screen.getByText("20/100")).toBeInTheDocument();
    expect(screen.getByText("Điểm: 1/5")).toBeInTheDocument();
    expect(screen.getByText("Không được đánh giá trong buổi phỏng vấn này")).toBeInTheDocument();
    expect(screen.queryByText("1/100")).not.toBeInTheDocument();
  });

  it("loads an available transcript when result reconciliation marked the interview failed",async()=>{
    api.getRecruitmentInterview.mockResolvedValue({...interview,status:"FAILED",overallScore:null,englishBand:null});
    api.getInterviewTranscript.mockResolvedValue({deliveryStatus:"PENDING_RESULT",expectedTurnCount:0,persistedTurnCount:1,page:0,size:100,turns:[
      {turnId:"turn-candidate",sequence:1,speaker:"CANDIDATE",turnKind:"CANDIDATE_UTTERANCE",sectionId:"section",questionId:"question",languageTag:"vi-VN",transcript:"Tôi đã hoàn thành phần trả lời."},
    ]});

    render(<InterviewDetailPage interviewId="interview-id"/>);
    await screen.findByText("Thông tin lịch");
    fireEvent.click(screen.getByRole("tab",{name:"Bản ghi lời thoại"}));

    expect(await screen.findByText("Tôi đã hoàn thành phần trả lời.")).toBeInTheDocument();
    expect(api.getInterviewTranscript).toHaveBeenCalledWith(api.request,"interview-id");
  });

  it("offers the development redial when completed eligibility allows it",async()=>{
    api.getDialEligibility.mockResolvedValue({allowed:true,reason:null,windowOpensAt:null,windowClosesAt:null,serverTime:"2026-07-26T12:45:00Z"});

    render(<InterviewDetailPage interviewId="interview-id"/>);

    expect(await screen.findByRole("button",{name:"Gọi lại (môi trường phát triển)"})).toBeInTheDocument();
  });

  it("offers a direct development redial instead of a new invitation after failure",async()=>{
    api.getRecruitmentInterview.mockResolvedValue({...interview,status:"FAILED",overallScore:null,englishBand:null});
    api.getDialEligibility.mockResolvedValue({allowed:true,reason:null,windowOpensAt:null,windowClosesAt:null,serverTime:"2026-07-26T12:45:00Z"});

    render(<InterviewDetailPage interviewId="interview-id"/>);

    expect(await screen.findByRole("button",{name:"Gọi lại (môi trường phát triển)"})).toBeInTheDocument();
    expect(api.getDialEligibility).toHaveBeenCalledWith(api.request,"interview-id");
    expect(screen.queryByRole("button",{name:"Gửi lời mời mới"})).not.toBeInTheDocument();
  });

  it("offers a direct development redial instead of a new invitation after consent is declined",async()=>{
    api.getRecruitmentInterview.mockResolvedValue({...interview,status:"DECLINED",overallScore:null,englishBand:null});
    api.getDialEligibility.mockResolvedValue({allowed:true,reason:null,windowOpensAt:null,windowClosesAt:null,serverTime:"2026-07-26T12:45:00Z"});

    render(<InterviewDetailPage interviewId="interview-id"/>);

    expect(await screen.findByRole("button",{name:"Gọi lại (môi trường phát triển)"})).toBeInTheDocument();
    expect(screen.queryByRole("button",{name:"Gửi lời mời mới"})).not.toBeInTheDocument();
  });

  it("explains a carrier busy result instead of displaying the Twilio enum",async()=>{
    api.getRecruitmentInterview.mockResolvedValue({...interview,status:"NO_ANSWER",overallScore:null,englishBand:null});
    api.getInterviewAttempts.mockResolvedValue([{attemptNumber:26,status:"NO_ANSWER",failureCode:"TWILIO_BUSY",createdAt:"2026-07-27T02:06:12Z",answeredAt:null,consentedAt:null,terminalAt:"2026-07-27T02:06:39Z"}]);
    api.getDialEligibility.mockResolvedValue({allowed:true,reason:null,windowOpensAt:null,windowClosesAt:null,serverTime:"2026-07-27T02:07:00Z"});

    render(<InterviewDetailPage interviewId="interview-id"/>);
    fireEvent.click(await screen.findByRole("tab",{name:"Lần gọi (1)"}));

    expect(screen.getByText(/Nhà mạng của số đích báo máy bận/)).toBeInTheDocument();
    expect(screen.queryByText(/TWILIO BUSY/)).not.toBeInTheDocument();
  });

  it("replaces provider and media diagnostic codes with Vietnamese guidance",async()=>{
    api.getRecruitmentInterview.mockResolvedValue({...interview,status:"FAILED",overallScore:null,englishBand:null});
    api.getInterviewAttempts.mockResolvedValue([
      {attemptNumber:1,status:"FAILED",failureCode:"TWILIO CALLBACK HTTP 400",createdAt:"2026-07-27T02:06:12Z",answeredAt:null,consentedAt:null,terminalAt:"2026-07-27T02:06:39Z"},
      {attemptNumber:2,status:"FAILED",failureCode:"CONSENT AUDIO TEST RETRY",createdAt:"2026-07-27T02:07:12Z",answeredAt:null,consentedAt:null,terminalAt:"2026-07-27T02:07:39Z"},
      {attemptNumber:3,status:"FAILED",failureCode:"MEDIA STREAM HANDSHAKE ERROR",createdAt:"2026-07-27T02:08:12Z",answeredAt:null,consentedAt:null,terminalAt:"2026-07-27T02:08:39Z"},
      {attemptNumber:4,status:"FAILED",failureCode:"CARTESIA TTS ADAPTER FAILURE",createdAt:"2026-07-27T02:09:12Z",answeredAt:null,consentedAt:null,terminalAt:"2026-07-27T02:09:39Z"},
    ]);

    render(<InterviewDetailPage interviewId="interview-id"/>);
    fireEvent.click(await screen.findByRole("tab",{name:"Lần gọi (4)"}));

    expect(screen.getByText(/Nhà cung cấp điện thoại không thể kết nối/)).toBeInTheDocument();
    expect(screen.getByText(/Không thể hoàn tất lời hướng dẫn xác nhận/)).toBeInTheDocument();
    expect(screen.getByText(/Không thể thiết lập kết nối âm thanh/)).toBeInTheDocument();
    expect(screen.getByText(/Dịch vụ giọng nói phỏng vấn/)).toBeInTheDocument();
    expect(screen.queryByText(/TWILIO CALLBACK HTTP 400/)).not.toBeInTheDocument();
    expect(screen.queryByText(/MEDIA STREAM HANDSHAKE ERROR/)).not.toBeInTheDocument();
  });

  it("uses the same friendly message in the result report",async()=>{
    api.getInterviewResult.mockResolvedValue({
      terminalKind:"FAILED",deliveryStatus:"COMPLETE",completionReason:null,
      failureCode:"CARTESIA TTS ADAPTER FAILURE",partial:true,overallScore:null,englishBand:null,
      advisoryOnly:true,englishWarning:"",sections:[],
    });

    render(<InterviewDetailPage interviewId="interview-id"/>);
    fireEvent.click(await screen.findByRole("tab",{name:"Báo cáo kết quả"}));

    expect(screen.getByText(/Dịch vụ giọng nói phỏng vấn đang tạm thời không khả dụng/)).toBeInTheDocument();
    expect(screen.queryByText(/CARTESIA TTS ADAPTER FAILURE/)).not.toBeInTheDocument();
  });

  it("explains and disables manual calling before the scheduled window",async()=>{
    api.getRecruitmentInterview.mockResolvedValue({...interview,status:"SCHEDULED",scheduledStartAt:"2026-07-27T02:00:00Z",scheduledEndAt:"2026-07-27T02:30:00Z"});
    api.getDialEligibility.mockResolvedValue({allowed:false,reason:"OUTSIDE_DIAL_WINDOW",windowOpensAt:"2026-07-27T01:59:45Z",windowClosesAt:"2026-07-27T02:02:00Z",serverTime:"2026-07-26T12:45:00Z"});
    render(<InterviewDetailPage interviewId="interview-id"/>);
    expect(await screen.findByRole("button",{name:"manualDial"})).toBeDisabled();
    expect(screen.getByText(/dialNotOpen/)).toBeInTheDocument();
  });
});
