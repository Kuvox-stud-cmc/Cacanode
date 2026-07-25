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

async function mockRecruiter(page: Page, masterEnabled: boolean) {
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
    await route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ message: `Unmocked ${path}` }) });
  });
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
