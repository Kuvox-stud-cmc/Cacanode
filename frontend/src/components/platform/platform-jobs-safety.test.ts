import { describe, expect, it } from "vitest"
import { readFileSync } from "node:fs"
import { resolve } from "node:path"

describe("platform job inventory safety", () => {
  const list = readFileSync(resolve(process.cwd(), "src/app/[locale]/platform/jobs/page.tsx"), "utf8")
  const detail = readFileSync(resolve(process.cwd(), "src/app/[locale]/platform/jobs/[jobId]/page.tsx"), "utf8")
  const client = readFileSync(resolve(process.cwd(), "src/lib/platform-api.ts"), "utf8")

  it("has read-only navigation and no recruitment mutation controls", () => {
    expect(`${list}\n${detail}`).not.toMatch(/createRecruitment|publishJob|pauseJob|closeJob|archiveJob|deleteJob|method:\s*["'](?:POST|PUT|PATCH|DELETE)/)
    expect(detail).toContain("job.visibleOnPublicBoard&&")
    expect(detail).toContain("/jobs/${job.publicId}")
  })

  it("keeps PII and sensitive recruitment configuration out of platform contracts", () => {
    expect(client).not.toMatch(/candidateId|applicationId|cvPolicy|screening|descriptionHtml|description:|transcript|evaluation|provider/)
  })
})
