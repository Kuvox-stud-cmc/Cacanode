import { describe, expect, it } from "vitest";
import { nationalPhoneDigits, phoneCountryName, toE164 } from "./recruitment-phone";

describe("recruitment phone numbers", () => {
  it("combines the selected calling code with national digits", () => {
    expect(toE164("VN", "090 123 4567")).toBe("+84901234567");
    expect(toE164("US", "(415) 555-2671")).toBe("+14155552671");
    expect(toE164("SG", "9123 4567")).toBe("+6591234567");
  });

  it("removes national trunk prefixes and enforces E.164 length", () => {
    expect(nationalPhoneDigits("(090)-123-4567")).toBe("901234567");
    expect(toE164("VN", "123")).toBeNull();
    expect(toE164("IN", "123456789012345")).toBeNull();
  });

  it("provides localized country names", () => {
    expect(phoneCountryName("VN", "en")).toMatch(/Vietnam/i);
    expect(phoneCountryName("VN", "vi")).toMatch(/Việt Nam/i);
  });
});
