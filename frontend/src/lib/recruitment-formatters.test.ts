import { describe, expect, it } from "vitest";
import { formatEnumLabel } from "./recruitment-formatters";

describe("recruitment template labels", () => {
  it("uses bilingual user-friendly section-kind labels", () => {
    expect(formatEnumLabel("CORE", "en")).toBe("Core interview");
    expect(formatEnumLabel("ENGLISH_SCREEN", "en")).toBe("English proficiency check");
    expect(formatEnumLabel("CORE", "vi")).toBe("Phỏng vấn chuyên môn");
    expect(formatEnumLabel("ENGLISH_SCREEN", "vi")).toBe("Kiểm tra năng lực tiếng Anh");
  });

  it("localizes overview job, application, and interview statuses", () => {
    expect(formatEnumLabel("PUBLISHED", "en")).toBe("Published");
    expect(formatEnumLabel("PUBLISHED", "vi")).toBe("Đã đăng");
    expect(formatEnumLabel("UNDER_REVIEW", "vi")).toBe("Đang xem xét");
    expect(formatEnumLabel("NO_ANSWER", "vi")).toBe("Không nhấc máy");
  });

  it("uses clear verification and CV analysis labels", () => {
    expect(formatEnumLabel("SUBMITTED_UNVERIFIED","en")).toBe("Awaiting Email Verification");
    expect(formatEnumLabel("SUBMITTED_UNVERIFIED","vi")).toBe("Chờ xác minh email");
    expect(formatEnumLabel("NOT_REQUESTED","vi")).toBe("Chưa yêu cầu");
    expect(formatEnumLabel("SKIPPED_QUOTA","en")).toBe("Skipped — Quota Exhausted");
  });
});
