import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page, type Route } from "@playwright/test";

const tenantId = "11111111-1111-4111-8111-111111111111";

async function json(route: Route, body: unknown, headers: Record<string, string> = {}) {
  await route.fulfill({
    status: 200,
    contentType: "application/json",
    headers,
    body: JSON.stringify(body),
  });
}

async function assertNoSeriousAxeViolations(page: Page) {
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.violations.filter((item) => item.impact === "critical" || item.impact === "serious")).toEqual([]);
}

async function mockRecruiter(page: Page, masterEnabled: boolean, jobStatus = "DRAFT") {
  let lastJobWrite: Record<string, unknown> | null = null;
  const job = {
    id: "55555555-5555-4555-8555-555555555555", publicId: "66666666-6666-4666-8666-666666666666",
    title: "Platform Engineer", description: "Legacy description", descriptionHtml: null,
    department: "Engineering", location: "Ho Chi Minh City", employmentType: "FULL_TIME", workMode: "HYBRID",
    experienceLevel: "MID", language: "en-US", status: jobStatus, cvPolicy: "OPTIONAL",
    automationModeOverride: null, cvAiModeOverride: null, effectiveAutomationMode: null, effectiveCvAiMode: null,
    recordingEnabled: false, recordingRetentionDays: 0, templateRevisionId: null, closingAt: null,
    publishedAt: jobStatus === "PUBLISHED" ? "2026-07-20T03:00:00" : null, pausedAt: null, closedAt: null,
    archivedAt: null, companyName: "CacaNode", companySlug: "cacanode", version: 0, screeningQuestions: [],
    createdAt: "2026-07-20T03:00:00", updatedAt: "2026-07-20T03:00:00",
  };
  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/auth/refresh")) return json(route, {
      accessToken: "playwright-access-token",
      tokenType: "Bearer",
      expiresIn: 900,
      user: { userId: "22222222-2222-4222-8222-222222222222", tenantId, email: "admin@example.com", fullName: "Recruitment Admin", role: "TENANT_ADMIN", plan: "BUSINESS" },
    });
    if (path.endsWith("/billing/account")) return json(route, {
      planCode: "BUSINESS", status: "ACTIVE", interval: "MONTHLY", trialEndsAt: null, paidThroughAt: null,
      graceEndsAt: null, quotaPeriodStart: "2026-07-01T00:00:00Z", nextQuotaResetAt: "2026-08-01T00:00:00Z",
      messages: { used: 0, limit: 1000, overLimit: false }, documents: { used: 0, limit: 100, overLimit: false },
      teamMembers: { used: 1, limit: 20, overLimit: false }, storageMb: { used: 0, limit: 1000, overLimit: false },
      activeJobs: { used: 1, reserved: 0, limit: 20, overLimit: false }, verifiedApplications: { used: 2, reserved: 0, limit: 1000, overLimit: false },
      interviewSeconds: { used: 60, reserved: 0, limit: 36000, overLimit: false }, cvAnalyses: { used: 1, reserved: 0, limit: 1000, overLimit: false },
      recruitmentStorageBytes: { used: 1024, reserved: 0, limit: 10737418240, overLimit: false },
      features: { apiAccess: true, webhooks: true, advancedAnalytics: true, customBranding: true }, pendingPayment: null, cancelAtPeriodEnd: false,
    });
    if (path.endsWith("/recruitment/capabilities")) return json(route, {
      tenantId, rolloutStage: masterEnabled ? "INTERNAL" : "OFF", masterEnabled, publicJobsEnabled: masterEnabled,
      automationEnabled: masterEnabled, cvAiEnabled: masterEnabled, callingEnabled: masterEnabled,
      recordingEnabled: false, publicDiscoveryEnabled: false,
      blockers: masterEnabled ? [] : ["PLATFORM_NOT_ACTIVATED"],
    });
    if (path.endsWith("/recruitment/settings")) return json(route, {
      defaultAutomationMode: "MANUAL", cvAiMode: "OFF", defaultTemplateRevisionId: null, recordingEnabled: false,
      recordingRetentionDays: 0, schedulingTimezone: "Asia/Ho_Chi_Minh", minimumNoticeMinutes: 60,
      bookingHorizonDays: 14, invitationLifetimeDays: 7, rescheduleCutoffMinutes: 120, reminderOffsetsMinutes: [1440], version: 0,
    });
    if (path.endsWith("/recruitment/availability")) return json(route, {
      timezone: "Asia/Ho_Chi_Minh", weeklyWindows: [{ dayOfWeek: 1, startLocal: "09:00", endLocal: "17:00" }], exceptions: [], version: 0,
    });
    if (path.endsWith("/recruitment/templates")) return json(route, [], { "X-Total-Count": "0" });
    if (path.endsWith(`/recruitment/jobs/${job.id}/preview`)) return json(route, {
      publicId: job.publicId, tenantSlug: "cacanode", companyName: "CacaNode", title: job.title,
      description: "Build reliable systems", descriptionHtml: "<h2>What you will do</h2><ul><li><strong>Build reliable systems</strong></li></ul>",
      department: job.department, location: job.location, employmentType: job.employmentType, workMode: job.workMode,
      experienceLevel: job.experienceLevel, language: job.language, cvPolicy: job.cvPolicy, status: job.status,
      publishedAt: job.publishedAt, closingAt: null,
    }, { "Cache-Control": "no-store" });
    if (path.endsWith(`/recruitment/jobs/${job.id}`)) {
      if (route.request().method() === "PUT") {
        lastJobWrite = route.request().postDataJSON() as Record<string, unknown>;
        return json(route, { ...job, ...lastJobWrite, description: String(lastJobWrite.description ?? "") });
      }
      return json(route, job);
    }
    await route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ message: `Unmocked ${path}` }) });
  });
  return { job, lastJobWrite: () => lastJobWrite };
}

test("shows the platform activation blocker", async ({ page }) => {
  await mockRecruiter(page, false);
  await page.goto("/recruitment/setup");
  await expect(page.getByRole("status")).toContainText("PLATFORM_NOT_ACTIVATED");
  await expect(page.getByRole("button", { name: "Save" })).toHaveCount(0);
  await assertNoSeriousAxeViolations(page);
});

test("loads tenant-admin recruitment setup", async ({ page }) => {
  await mockRecruiter(page, true);
  await page.goto("/recruitment/setup");
  await expect(page.getByRole("heading", { name: "Recruitment setup" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Save" })).toBeEnabled();
  await assertNoSeriousAxeViolations(page);
});

test("authors and persists a formatted job description", async ({ page }) => {
  const mocked = await mockRecruiter(page, true);
  await page.goto(`/recruitment/jobs/${mocked.job.id}`);
  const editor = page.getByRole("textbox", { name: "Job description content" });
  await expect(editor).toContainText("Legacy description");
  await editor.click();
  await page.keyboard.press(process.platform === "darwin" ? "Meta+A" : "Control+A");
  await page.keyboard.type("Build reliable systems");
  await page.keyboard.press(process.platform === "darwin" ? "Meta+A" : "Control+A");
  await page.getByRole("button", { name: "Bold" }).click();
  await page.getByRole("button", { name: "Save draft" }).click();
  await expect.poll(() => mocked.lastJobWrite()).not.toBeNull();
  expect(String(mocked.lastJobWrite()?.descriptionHtml)).toContain("<strong>Build reliable systems</strong>");
  expect(mocked.lastJobWrite()?.description).toBe("Build reliable systems");
  await assertNoSeriousAxeViolations(page);
});

test("renders persisted recruiter preview without Apply", async ({ page }) => {
  const mocked = await mockRecruiter(page, true);
  await page.goto(`/recruitment/jobs/${mocked.job.id}/preview`);
  await expect(page.getByRole("status")).toContainText("Recruiter preview · DRAFT");
  await expect(page.getByRole("heading", { name: "What you will do" })).toBeVisible();
  await expect(page.getByRole("link", { name: /apply/i })).toHaveCount(0);
  await assertNoSeriousAxeViolations(page);
});

test("published unlisted jobs link to their exact public URL", async ({ page }) => {
  const mocked = await mockRecruiter(page, true, "PUBLISHED");
  await page.goto(`/recruitment/jobs/${mocked.job.id}`);
  await expect(page.getByRole("link", { name: "Public preview" })).toHaveAttribute("href", `/jobs/${mocked.job.publicId}`);
});

test("exchanges a candidate token and requests confirmed erasure", async ({ page }) => {
  await page.route("**/api/v1/public/applications/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/access")) return json(route, {
      csrfToken: "candidate-csrf",
      application: { applicationId: "33333333-3333-4333-8333-333333333333", jobPublicId: "job-public", companyName: "CacaNode", jobTitle: "Backend Engineer", status: "SUBMITTED", submittedAt: "2026-07-20T03:00:00Z", verifiedAt: "2026-07-20T03:01:00Z", withdrawnAt: null, cvPresent: true },
    });
    if (path.endsWith("/me/privacy-deletion-requests")) {
      expect(route.request().headers()["x-csrf-token"]).toBe("candidate-csrf");
      return json(route, { status: "PENDING_CONFIRMATION" });
    }
    await route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
  });
  await page.goto("/applications/manage#token=fragment-only-token");
  await expect(page).not.toHaveURL(/fragment-only-token/);
  await expect(page.getByRole("heading", { name: "Backend Engineer" })).toBeVisible();
  await page.getByRole("button", { name: "Delete my application data" }).click();
  await expect(page.getByRole("status")).toContainText("confirm within one hour");
  await assertNoSeriousAxeViolations(page);
});

test("exchanges an interview invitation and renders available slots", async ({ page }) => {
  await page.route("**/api/v1/public/interview-invitations/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/exchange")) return json(route, {
      csrfToken: "invitation-csrf",
      invitation: { interviewId: "44444444-4444-4444-8444-444444444444", companyName: "CacaNode", jobTitle: "AI Engineer", candidateName: "Candidate", status: "INVITED", scheduledStartAt: null, scheduledEndAt: null, schedulingTimezone: "Asia/Ho_Chi_Minh", invitationExpiresAt: "2026-07-31T00:00:00Z", rescheduleCount: 0 },
    });
    if (path.endsWith("/me/slots")) return json(route, {
      items: [{ startAt: "2026-07-28T02:00:00Z", endAt: "2026-07-28T02:30:00Z", schedulingTimezone: "Asia/Ho_Chi_Minh" }],
      nextFrom: null, schedulingTimezone: "Asia/Ho_Chi_Minh",
    });
    await route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
  });
  await page.goto("/applications/manage#invitation=fragment-only-invitation");
  await expect(page).not.toHaveURL(/fragment-only-invitation/);
  await expect(page.getByRole("heading", { name: "Choose an interview time" })).toBeVisible();
  await expect(page.getByRole("button", { name: /9:00/ })).toBeVisible();
  await assertNoSeriousAxeViolations(page);
});
