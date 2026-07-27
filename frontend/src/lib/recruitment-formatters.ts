export const TIMEZONE_OPTIONS = [
  { value: "Asia/Ho_Chi_Minh", label: "(UTC+07:00) Hà Nội, TP. Hồ Chí Minh, Bangkok, Jakarta" },
  { value: "Asia/Singapore", label: "(UTC+08:00) Singapore, Kuala Lumpur, Hồng Kông, Manila" },
  { value: "Asia/Tokyo", label: "(UTC+09:00) Tokyo, Seoul, Osaka" },
  { value: "Asia/Kolkata", label: "(UTC+05:30) Ấn Độ, New Delhi, Mumbai" },
  { value: "Asia/Dubai", label: "(UTC+04:00) Dubai, Abu Dhabi, Muscat" },
  { value: "UTC", label: "(UTC+00:00) Giờ Quốc tế Phối hợp (UTC)" },
  { value: "Europe/London", label: "(UTC+00:00 / DST) Luân Đôn, Dublin, Lisbon" },
  { value: "Europe/Paris", label: "(UTC+01:00 / DST) Paris, Berlin, Roma, Madrid, Amsterdam" },
  { value: "America/New_York", label: "(UTC-05:00 / DST) Giờ Miền Đông (New York, Toronto)" },
  { value: "America/Chicago", label: "(UTC-06:00 / DST) Giờ Miền Trung (Chicago, Houston)" },
  { value: "America/Denver", label: "(UTC-07:00 / DST) Giờ Miền Núi (Denver, Calgary)" },
  { value: "America/Los_Angeles", label: "(UTC-08:00 / DST) Giờ Thái Bình Dương (Los Angeles, Vancouver)" },
  { value: "Australia/Sydney", label: "(UTC+10:00 / DST) Sydney, Melbourne, Brisbane" },
  { value: "Pacific/Auckland", label: "(UTC+12:00 / DST) Auckland, Wellington" },
];

export function formatEnumLabel(value: string | null | undefined, locale: string = "vi"): string {
  if (!value) return "—";
  const isVi = locale.startsWith("vi");

  const labelsVi: Record<string, string> = {
    // Employment types
    FULL_TIME: "Toàn thời gian",
    PART_TIME: "Bán thời gian",
    CONTRACT: "Hợp đồng",
    TEMPORARY: "Tạm thời",
    INTERNSHIP: "Thực tập",

    // Work modes
    ONSITE: "Tại văn phòng",
    REMOTE: "Làm việc từ xa",
    HYBRID: "Linh hoạt (Hybrid)",

    // Experience levels
    ENTRY: "Mới tốt nghiệp / Entry",
    JUNIOR: "Sơ cấp (Junior)",
    MID: "Trung cấp (Mid)",
    SENIOR: "Cao cấp (Senior)",
    LEAD: "Trưởng nhóm (Lead)",
    EXECUTIVE: "Lãnh đạo (Executive)",

    // CV policy
    REQUIRED: "Bắt buộc gửi CV",
    OPTIONAL: "Tùy chọn CV",
    DISABLED: "Không nhận CV",

    // Automation modes
    MANUAL: "Duyệt thủ công",
    AUTO_INVITE_ALL: "Tự động mời tất cả",
    AUTO_INVITE_MATCHING: "Tự động mời ứng viên đạt sàng lọc",

    // CV AI modes
    OFF: "Tắt",
    SUMMARY_ONLY: "Chỉ tóm tắt CV",
    PERSONALIZED_QUESTIONS: "Tóm tắt & Tạo câu hỏi cá nhân hóa",

    // Interview template section kinds
    CORE: "Phỏng vấn chuyên môn",
    ENGLISH_SCREEN: "Kiểm tra năng lực tiếng Anh",

    // Application statuses
    AWAITING_CANDIDATE: "Chờ ứng viên hoàn thiện",
    SUBMITTED_UNVERIFIED: "Chờ xác minh email",
    SUBMITTED: "Đã nộp",
    UNDER_REVIEW: "Đang xem xét",
    INTERVIEW_INVITED: "Đã mời phỏng vấn",
    INTERVIEW_SCHEDULED: "Đã lên lịch phỏng vấn",
    INTERVIEW_COMPLETED: "Đã hoàn thành phỏng vấn",
    SHORTLISTED: "Đã vào danh sách ngắn",
    REJECTED: "Từ chối",
    WITHDRAWN: "Ứng viên đã rút",

    // Job statuses
    DRAFT: "Bản nháp",
    PUBLISHED: "Đã đăng",
    PAUSED: "Tạm dừng",
    CLOSED: "Đã đóng",
    ARCHIVED: "Đã lưu trữ",

    // Interview statuses
    INVITED: "Đã mời",
    SCHEDULED: "Đã lên lịch",
    PREPARING: "Đang chuẩn bị",
    READY: "Sẵn sàng",
    DIALING: "Đang quay số",
    CALLING: "Đang gọi",
    RINGING: "Đang đổ chuông",
    CONSENT_PENDING: "Chờ đồng ý ghi âm",
    IN_PROGRESS: "Đang phỏng vấn",
    COMPLETED: "Đã hoàn thành",
    NO_ANSWER: "Không nhấc máy",
    DECLINED: "Từ chối phỏng vấn",
    FAILED: "Cuộc gọi thất bại",
    CANCELLED: "Đã hủy",
    EXPIRED: "Đã hết hạn",

    // Interview delivery and result values
    DISPATCHING: "Đang gửi",
    SENT: "Đã gửi",
    INVITATION: "Lời mời phỏng vấn",
    CONFIRMATION: "Xác nhận lịch",
    RESCHEDULE_CONFIRMATION: "Xác nhận đổi lịch",
    REMINDER: "Nhắc lịch",
    FINISHED: "Hoàn tất",
    CANDIDATE_STOPPED: "Ứng viên dừng",
    TIME_LIMIT: "Hết thời gian",
    PARTIAL: "Chưa đầy đủ",
    COMPLETE: "Đầy đủ",

    // CV analysis statuses
    NOT_REQUESTED: "Chưa yêu cầu",
    PENDING: "Đang xử lý",
    SKIPPED_QUOTA: "Bỏ qua do hết hạn mức",
  };

  const labelsEn: Record<string, string> = {
    FULL_TIME: "Full-time",
    PART_TIME: "Part-time",
    CONTRACT: "Contract",
    TEMPORARY: "Temporary",
    INTERNSHIP: "Internship",

    ONSITE: "On-site",
    REMOTE: "Remote",
    HYBRID: "Hybrid",

    ENTRY: "Entry Level",
    JUNIOR: "Junior",
    MID: "Mid Level",
    SENIOR: "Senior",
    LEAD: "Lead",
    EXECUTIVE: "Executive",

    REQUIRED: "CV Required",
    OPTIONAL: "CV Optional",
    DISABLED: "No CV Accepted",

    MANUAL: "Manual Review",
    AUTO_INVITE_ALL: "Auto-Invite All",
    AUTO_INVITE_MATCHING: "Auto-Invite Matching",

    OFF: "Off",
    SUMMARY_ONLY: "Summary Only",
    PERSONALIZED_QUESTIONS: "Summary & Questions",

    CORE: "Core interview",
    ENGLISH_SCREEN: "English proficiency check",

    AWAITING_CANDIDATE: "Awaiting Candidate",
    SUBMITTED_UNVERIFIED: "Awaiting Email Verification",
    SUBMITTED: "Submitted",
    UNDER_REVIEW: "Under Review",
    INTERVIEW_INVITED: "Interview Invited",
    INTERVIEW_SCHEDULED: "Interview Scheduled",
    INTERVIEW_COMPLETED: "Interview Completed",
    SHORTLISTED: "Shortlisted",
    REJECTED: "Rejected",
    WITHDRAWN: "Withdrawn",

    DRAFT: "Draft",
    PUBLISHED: "Published",
    PAUSED: "Paused",
    CLOSED: "Closed",
    ARCHIVED: "Archived",

    INVITED: "Invited",
    SCHEDULED: "Scheduled",
    PREPARING: "Preparing Call",
    READY: "Ready",
    DIALING: "Dialing",
    CALLING: "Calling",
    RINGING: "Ringing",
    CONSENT_PENDING: "Consent Pending",
    IN_PROGRESS: "In Progress",
    COMPLETED: "Completed",
    NO_ANSWER: "No Answer",
    DECLINED: "Declined",
    FAILED: "Failed",
    CANCELLED: "Cancelled",
    EXPIRED: "Expired",

    DISPATCHING: "Sending",
    SENT: "Sent",
    INVITATION: "Interview Invitation",
    CONFIRMATION: "Schedule Confirmation",
    RESCHEDULE_CONFIRMATION: "Reschedule Confirmation",
    REMINDER: "Reminder",
    FINISHED: "Finished",
    CANDIDATE_STOPPED: "Stopped by Candidate",
    TIME_LIMIT: "Time Limit Reached",
    PARTIAL: "Partial",
    COMPLETE: "Complete",

    NOT_REQUESTED: "Not Requested",
    PENDING: "Processing",
    SKIPPED_QUOTA: "Skipped — Quota Exhausted",
  };

  const dict = isVi ? labelsVi : labelsEn;
  return dict[value] || value.replaceAll("_", " ");
}

export function formatTimezoneLabel(tz: string | null | undefined): string {
  if (!tz) return "UTC";
  const found = TIMEZONE_OPTIONS.find((opt) => opt.value === tz);
  return found ? found.label : tz;
}
