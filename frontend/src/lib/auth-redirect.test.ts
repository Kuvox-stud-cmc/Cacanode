import { describe, expect, it } from "vitest"
import { destinationForRole, safeInternalPath } from "@/lib/auth-redirect"

describe("role-aware auth destinations", () => {
  it("always sends platform administrators to the platform surface", () => {
    expect(destinationForRole("PLATFORM_ADMIN", "/dashboard?tab=quota")).toBe("/platform")
    expect(destinationForRole("PLATFORM_ADMIN", "/vi/platform/staff")).toBe("/platform")
  })

  it("prevents customer roles from entering platform destinations", () => {
    expect(destinationForRole("TENANT_ADMIN", "/platform/staff")).toBe("/dashboard")
    expect(destinationForRole("USER", "/vi/platform")).toBe("/dashboard")
  })

  it("preserves safe localized customer destinations", () => {
    expect(safeInternalPath("/vi/documents?page=2")).toBe("/documents?page=2")
    expect(destinationForRole("USER", "/en/dashboard#recent")).toBe("/dashboard#recent")
  })
})
