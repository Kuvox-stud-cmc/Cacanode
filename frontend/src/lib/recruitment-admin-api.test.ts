import { describe, expect, it, vi } from "vitest";
import { getRecordingBlob, getRecruitmentJobPreview, listRecruitmentApplications, refreshCvAnalysis } from "@/lib/recruitment-admin-api";

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

  it("requests authenticated previews without caching", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://localhost/api/v1");
    const request = vi.fn(async () => new Response(JSON.stringify({ publicId: "public-1" }), { status: 200 }));
    await getRecruitmentJobPreview(request, "job-1");
    expect(request).toHaveBeenCalledWith("http://localhost/api/v1/recruitment/jobs/job-1/preview", { cache: "no-store" });
  });

  it("loads playback and downloads through the authenticated request helper", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://localhost/api/v1");
    const request = vi.fn(async () => new Response(new Blob(["recording"], { type: "audio/mpeg" }), { status: 200 }));

    await getRecordingBlob(request, "interview-1", "recording-1");
    await getRecordingBlob(request, "interview-1", "recording-1", true);

    expect(request).toHaveBeenNthCalledWith(1,
      "http://localhost/api/v1/recruitment/interviews/interview-1/recordings/recording-1/playback", { cache: "no-store" });
    expect(request).toHaveBeenNthCalledWith(2,
      "http://localhost/api/v1/recruitment/interviews/interview-1/recordings/recording-1/download", { cache: "no-store" });
  });

  it("sends the stable refresh request id for server-side idempotency", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://localhost/api/v1");
    const request = vi.fn(async () => new Response(JSON.stringify({ status: "COMPLETED" }), { status: 200 }));

    await refreshCvAnalysis(request, "application-1", "11111111-1111-4111-8111-111111111111");

    expect(request).toHaveBeenCalledWith(
      "http://localhost/api/v1/recruitment/applications/application-1/cv-analysis/refresh",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ requestId: "11111111-1111-4111-8111-111111111111" }),
      }),
    );
  });
});
