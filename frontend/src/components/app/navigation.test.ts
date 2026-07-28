import { describe, expect, it } from "vitest";
import { appNavigation } from "@/components/app/navigation";

describe("recruitment navigation", () => {
  it("defines one feature-gated global item with a beta badge", () => {
    const items = appNavigation.filter((item) => item.href === "/recruitment");
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({ recruitmentOnly: true, beta: true, labelKey: "recruitment" });
  });
});
