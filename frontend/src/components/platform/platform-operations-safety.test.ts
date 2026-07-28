import { describe, expect, it } from "vitest"
import { readFileSync } from "node:fs"
import { resolve } from "node:path"

describe("platform operations safety", () => {
  const page = readFileSync(resolve(process.cwd(), "src/app/[locale]/platform/operations/page.tsx"), "utf8")
  const client = readFileSync(resolve(process.cwd(), "src/lib/platform-api.ts"), "utf8")

  it("polls together only while visible and supports manual refresh", () => {
    expect(page).toContain("Promise.allSettled")
    expect(page).toContain("15_000")
    expect(page).toContain('document.visibilityState === "visible"')
    expect(page).toContain('addEventListener("visibilitychange"')
    expect(page).toContain("onClick={() => void load()}")
  })

  it("contains no operational mutation controls or sensitive infrastructure fields", () => {
    expect(page).not.toMatch(/method:\s*["'](?:POST|PUT|PATCH|DELETE)/)
    expect(`${page}\n${client}`).not.toMatch(/\b(?:brokerName|queueName|docker|credential|secretKey|exceptionText|stackTrace|payload)\b/)
    expect(page).not.toMatch(/https?:\/\//)
  })
})
