import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PublicJob } from "@/lib/recruitment-api";
import { ApplicationForm } from "./ApplicationForm";

const submitApplication = vi.fn();
const clearLocaleDraft = vi.fn();

vi.mock("next-intl", () => ({
  useLocale: () => "en",
  useTranslations: () => (key: string) => key,
}));
vi.mock("@/lib/recruitment-api", () => ({
  submitApplication: (...args: unknown[]) => submitApplication(...args),
}));
vi.mock("@/hooks/useLocaleChangeDraft", () => ({
  useLocaleChangeDraft: () => clearLocaleDraft,
}));

const job = {
  publicId: "job-public-id",
  cvPolicy: "DISABLED",
  cvAiMode: "OFF",
  screeningQuestions: [],
} as PublicJob;

describe("ApplicationForm phone submission", () => {
  beforeEach(() => {
    submitApplication.mockReset();
    clearLocaleDraft.mockReset();
    submitApplication.mockResolvedValue({ accepted: true });
  });

  it("submits canonical E.164 without UI-only phone fields", async () => {
    render(<ApplicationForm job={job} />);
    fireEvent.change(screen.getByRole("textbox", { name: "fullName" }), { target: { value: "Ada Lovelace" } });
    fireEvent.change(screen.getByRole("textbox", { name: "email" }), { target: { value: "ada@example.com" } });
    fireEvent.change(screen.getByRole("combobox", { name: "phoneCountry" }), { target: { value: "US" } });
    fireEvent.change(screen.getByRole("textbox", { name: "phone" }), { target: { value: "(415) 555-2671" } });
    fireEvent.click(screen.getByRole("checkbox", { name: "privacyConsent" }));
    fireEvent.click(screen.getByRole("button", { name: "submit" }));

    await waitFor(() => expect(submitApplication).toHaveBeenCalledOnce());
    const payload = submitApplication.mock.calls[0][1];
    expect(payload).toMatchObject({ phone: "+14155552671", locale: "en-US" });
    expect(payload).not.toHaveProperty("phoneCountry");
    expect(payload).not.toHaveProperty("phoneNational");
    expect(clearLocaleDraft).toHaveBeenCalledOnce();
  });
});
