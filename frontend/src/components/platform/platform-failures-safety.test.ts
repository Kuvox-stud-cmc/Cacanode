import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

describe("platform failure explorer safety", () => {
  const page = readFileSync(resolve(process.cwd(), "src/app/[locale]/platform/failures/page.tsx"), "utf8")
  const tenant = readFileSync(resolve(process.cwd(), "src/app/[locale]/platform/tenants/[tenantId]/page.tsx"), "utf8")

  it("is read-only and contains no sensitive owner fields", () => {
    const source = `${page}\n${tenant}`
    expect(source).not.toMatch(/method:\s*["'](?:POST|PUT|PATCH|DELETE)/)
    expect(source).not.toMatch(/\b(?:recipient|phone|email|providerReference|checkoutUrl|storageKey|rawError|failureText)\b/)
    expect(page).not.toMatch(/https?:\/\//)
  })

  it("links only to controlled tenant and job detail routes", () => {
    const routes = [...page.matchAll(/`(\/platform\/[^`]+)`/g)].map(match => match[1])
    expect(routes).toEqual(expect.arrayContaining(["/platform/jobs/${item.resourceId}", "/platform/tenants/${item.tenantId}"]))
    expect(page).not.toMatch(/\/platform\/(?:applications|candidates|interviews|recordings|webhooks|payments)\//)
  })
})
