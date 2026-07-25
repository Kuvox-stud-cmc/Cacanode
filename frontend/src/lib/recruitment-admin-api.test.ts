import { describe, expect, it, vi } from "vitest";
import { listRecruitmentApplications } from "@/lib/recruitment-admin-api";

describe("recruitment admin api", () => {
  it("keeps exact candidate filtering and stable pagination metadata", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://localhost/api/v1");
    const request = vi.fn(async () => new Response(JSON.stringify([{ id: "application-1" }]), {
      status: 200, headers: { "Content-Type": "application/json", "X-Total-Count": "41" },
    }));
    const page = await listRecruitmentApplications(request, {
      page: 2, size: 20, candidateId: "candidate-1", status: "UNDER_REVIEW",
    });
    expect(request).toHaveBeenCalledWith(expect.stringContaining("candidateId=candidate-1"));
    expect(request).toHaveBeenCalledWith(expect.stringContaining("page=2"));
    expect(page.total).toBe(41);
  });
});
