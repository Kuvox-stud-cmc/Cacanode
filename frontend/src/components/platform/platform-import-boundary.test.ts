import { describe, expect, it } from "vitest"
import { readFileSync } from "node:fs"
import { resolve } from "node:path"

describe("platform frontend import boundary", () => {
  it("does not depend on customer billing, workspace, or recruitment clients", () => {
    const files = [
      "src/components/platform/PlatformAdminShell.tsx",
      "src/app/[locale]/platform/page.tsx",
      "src/app/[locale]/platform/tenants/page.tsx",
      "src/app/[locale]/platform/tenants/[tenantId]/page.tsx",
      "src/app/[locale]/platform/jobs/page.tsx",
      "src/app/[locale]/platform/jobs/[jobId]/page.tsx",
      "src/app/[locale]/platform/failures/page.tsx",
      "src/app/[locale]/platform/operations/page.tsx",
      "src/app/[locale]/platform/staff/page.tsx",
      "src/lib/platform-api.ts",
      "src/lib/platform-staff-api.ts",
    ]
    const source = files.map(file => readFileSync(resolve(process.cwd(), file), "utf8")).join("\n")
    expect(source).not.toMatch(/billing-api|workspace-api|recruitment-admin-api/)
    expect(source).not.toMatch(/documents-api|users-api|tickets-api|analytics-api/)
  })
})
