import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RecruitmentSetupPage } from "./RecruitmentSetupPage";

const api = vi.hoisted(() => ({
  request: vi.fn(),
  getRecruitmentSettings: vi.fn(),
  getRecruitmentAvailability: vi.fn(),
  updateRecruitmentSettings: vi.fn(),
  updateRecruitmentAvailability: vi.fn(),
  clearLocaleDraft: vi.fn(),
}));

vi.mock("next-intl", () => ({
  useLocale: () => "en",
  useTranslations: () => (key: string) => key,
}));
vi.mock("@/components/providers/StoreProvider", () => ({
  useAuthStore: (selector: (state: { user: { role: string; tenantId: string } }) => unknown) =>
    selector({ user: { role: "TENANT_ADMIN", tenantId: "tenant-id" } }),
}));
vi.mock("@/hooks/useApiClient", () => ({
  useApiClient: () => ({ request: api.request }),
}));
vi.mock("@/hooks/useLocaleChangeDraft", () => ({
  useLocaleChangeDraft: () => api.clearLocaleDraft,
}));
vi.mock("@/lib/recruitment-formatters", () => ({
  formatEnumLabel: (value: string) => value,
  TIMEZONE_OPTIONS: [{ value: "Asia/Ho_Chi_Minh", label: "Asia/Ho_Chi_Minh" }],
}));
vi.mock("@/lib/recruitment-admin-api", () => ({
  getRecruitmentSettings: api.getRecruitmentSettings,
  getRecruitmentAvailability: api.getRecruitmentAvailability,
  updateRecruitmentSettings: api.updateRecruitmentSettings,
  updateRecruitmentAvailability: api.updateRecruitmentAvailability,
}));

const settings = {
  defaultAutomationMode: "MANUAL",
  cvAiMode: "PERSONALIZED_QUESTIONS",
  defaultTemplateRevisionId: null,
  recordingEnabled: false,
  recordingRetentionDays: 0,
  schedulingTimezone: "Asia/Ho_Chi_Minh",
  minimumNoticeMinutes: 120,
  bookingHorizonDays: 30,
  invitationLifetimeDays: 7,
  rescheduleCutoffMinutes: 120,
  reminderOffsetsMinutes: [1440, 60],
  version: 3,
};
const availability = {
  timezone: "Asia/Ho_Chi_Minh",
  weeklyWindows: [{ dayOfWeek: 1, startLocal: "09:00", endLocal: "17:00" }],
  exceptions: [],
  version: 3,
};

describe("RecruitmentSetupPage save ordering", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getRecruitmentSettings.mockResolvedValue(settings);
    api.getRecruitmentAvailability.mockResolvedValue(availability);
  });

  it("saves settings before availability and passes the newly returned version", async () => {
    let resolveSettings: ((value: typeof settings) => void) | undefined;
    api.updateRecruitmentSettings.mockReturnValue(
      new Promise<typeof settings>((resolve) => {
        resolveSettings = resolve;
      }),
    );
    api.updateRecruitmentAvailability.mockResolvedValue({ ...availability, version: 5 });

    render(<RecruitmentSetupPage />);

    const save = await screen.findByRole("button", { name: "save" });
    fireEvent.click(save);

    expect(api.updateRecruitmentSettings).toHaveBeenCalledOnce();
    expect(api.updateRecruitmentAvailability).not.toHaveBeenCalled();
    expect(save).toBeDisabled();

    resolveSettings?.({ ...settings, version: 4 });

    await waitFor(() =>
      expect(api.updateRecruitmentAvailability).toHaveBeenCalledWith(api.request, {
        version: 4,
        weeklyWindows: availability.weeklyWindows,
        exceptions: availability.exceptions,
      }),
    );
    expect(await screen.findByText("saved")).toBeInTheDocument();
    expect(api.clearLocaleDraft).toHaveBeenCalledOnce();
    expect(save).not.toBeDisabled();
  });
});
