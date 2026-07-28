import { getPlanPresentation } from "@/components/billing/PlanStatusBadge";
import { describe, expect, it } from "vitest";

describe("getPlanPresentation", () => {
  it("presents Business as active and Business grace as danger", () => {
    expect(getPlanPresentation("BUSINESS", "ACTIVE").className).toContain("emerald");
    expect(getPlanPresentation("BUSINESS", "GRACE").className).toContain("red");
  });
});
