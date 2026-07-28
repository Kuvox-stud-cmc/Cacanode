import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { JobBoard } from "./JobBoard";

const api = vi.hoisted(() => ({ listPublicJobs: vi.fn() }));

vi.mock("next-intl", () => {
  const translate = (key: string, values?: Record<string, unknown>) => {
    if (key === "careerTitle") return `Open positions at ${values?.company}`;
    if (key === "tenantBoard") return `${values?.company} careers`;
    return key;
  };
  return { useLocale: () => "en", useTranslations: () => translate };
});
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams() }));
vi.mock("@/i18n/navigation", () => ({
  usePathname: () => "/careers/cacanode-demo",
  useRouter: () => ({ replace: vi.fn() }),
}));
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
vi.mock("@/lib/recruitment-api", () => ({ listPublicJobs: api.listPublicJobs }));

describe("tenant careers job board", () => {
  beforeEach(() => {
    api.listPublicJobs.mockResolvedValue({
      items: [{
        publicId: "job-public", tenantSlug: "cacanode-demo", companyName: "CacaNode",
        title: "Platform Engineer", description: "Build reliable systems", descriptionHtml: null,
        department: "Engineering", location: "Ho Chi Minh City", employmentType: "FULL_TIME",
        workMode: "HYBRID", experienceLevel: "MID", language: "en-US", cvPolicy: "OPTIONAL",
        cvAiMode: "OFF", cvAiDisclosed: false, screeningQuestions: [],
        publishedAt: "2026-07-20T00:00:00", closingAt: "2026-08-20T00:00:00", discoverable: false,
      }],
      nextCursor: null,
    });
  });

  it("shows the owning company and requests its tenant-scoped jobs", async () => {
    render(<JobBoard tenantSlug="cacanode-demo" />);

    expect(await screen.findByRole("heading", { name: "Open positions at CacaNode" })).toBeInTheDocument();
    expect(screen.getByText("CacaNode careers")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Platform Engineer" })).toHaveAttribute("href", "/jobs/job-public");
    expect(api.listPublicJobs).toHaveBeenCalledWith(expect.any(URLSearchParams), "cacanode-demo");
  });
});
