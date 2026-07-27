import { describe, expect, it } from "vitest";
import {
  callFailureText,
  normalizeCallFailureCode,
  resolveCallFailureKey,
  type CallFailureMessageKey,
  type CallFailureTranslator,
} from "./recruitment-call-failures";

const messages: Partial<Record<CallFailureMessageKey, string>> = {
  "callFailures.TWILIO_CALLBACK_ERROR": "The phone provider could not reach the interview service.",
  "callFailures.CONSENT_AUDIO_ERROR": "The consent prompt could not be completed.",
  "callFailures.MEDIA_STREAM_ERROR": "The live interview audio connection could not be established.",
  "callFailures.VOICE_SERVICE_ERROR": "The interview voice service is temporarily unavailable.",
  "callFailures.TECHNICAL_ERROR": "The call encountered a technical problem.",
};

const translate = Object.assign(
  (key: CallFailureMessageKey) => messages[key] ?? key,
  { has: (key: CallFailureMessageKey) => Boolean(messages[key]) },
) as CallFailureTranslator;

describe("recruitment call failure messages", () => {
  it("normalizes provider codes that arrive as labels instead of enums", () => {
    expect(normalizeCallFailureCode("Twilio callback HTTP 400")).toBe("TWILIO_CALLBACK_HTTP_400");
    expect(normalizeCallFailureCode("cartesia-tts adapter failure")).toBe("CARTESIA_TTS_ADAPTER_FAILURE");
  });

  it.each([
    ["TWILIO CALLBACK HTTP 400", "callFailures.TWILIO_CALLBACK_ERROR"],
    ["CONSENT AUDIO TEST RETRY", "callFailures.CONSENT_AUDIO_ERROR"],
    ["MEDIA STREAM HANDSHAKE ERROR", "callFailures.MEDIA_STREAM_ERROR"],
    ["CARTESIA TTS ADAPTER FAILURE", "callFailures.VOICE_SERVICE_ERROR"],
  ] as const)("maps %s to a user-facing category", (code, expected) => {
    expect(resolveCallFailureKey(code)).toBe(expected);
    const text = callFailureText(code, translate, "en", { showTechnicalCode: false });
    expect(text).not.toContain(code);
    expect(text).not.toContain(code.replaceAll(" ", "_"));
  });

  it("hides unknown diagnostic codes in production-facing text", () => {
    expect(callFailureText("SOME_PROVIDER_DEEP_FAILURE", translate, "en", {
      showTechnicalCode: false,
    })).toBe("The call encountered a technical problem.");
  });
});
