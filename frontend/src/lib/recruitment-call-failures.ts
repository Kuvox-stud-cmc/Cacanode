import { formatEnumLabel } from "@/lib/recruitment-formatters";

const CALL_FAILURE_KEYS = {
  OUTSIDE_DIAL_WINDOW: "callFailures.OUTSIDE_DIAL_WINDOW",
  DIAL_WINDOW_EXPIRED: "callFailures.DIAL_WINDOW_EXPIRED",
  INTERVIEW_NOT_SCHEDULED: "callFailures.INTERVIEW_NOT_SCHEDULED",
  APPLICATION_NOT_SCHEDULED: "callFailures.APPLICATION_NOT_SCHEDULED",
  JOB_NOT_CALLABLE: "callFailures.JOB_NOT_CALLABLE",
  INVALID_DESTINATION: "callFailures.INVALID_DESTINATION",
  QUOTA_RESERVATION_INVALID: "callFailures.QUOTA_RESERVATION_INVALID",
  GLOBAL_CONCURRENCY_LIMIT: "callFailures.GLOBAL_CONCURRENCY_LIMIT",
  TENANT_CONCURRENCY_LIMIT: "callFailures.TENANT_CONCURRENCY_LIMIT",
  DESTINATION_DAILY_LIMIT: "callFailures.DESTINATION_DAILY_LIMIT",
  TENANT_DAILY_LIMIT: "callFailures.TENANT_DAILY_LIMIT",
  DESTINATION_TENANT_LIMIT: "callFailures.DESTINATION_TENANT_LIMIT",
  HIGH_RISK_DESTINATION: "callFailures.HIGH_RISK_DESTINATION",
  GUARD_UNAVAILABLE: "callFailures.GUARD_UNAVAILABLE",
  CONSENT_DECLINED: "callFailures.CONSENT_DECLINED",
  CONSENT_NOT_RECEIVED: "callFailures.CONSENT_NOT_RECEIVED",
  TWILIO_CREATE_REJECTED: "callFailures.TWILIO_CREATE_REJECTED",
  TWILIO_CREATE_REJECTED_13216: "callFailures.TWILIO_CREATE_REJECTED_13216",
  TWILIO_CREATE_REJECTED_21219: "callFailures.TWILIO_CREATE_REJECTED_21219",
  TWILIO_CREATE_UNCONFIRMED: "callFailures.TWILIO_CREATE_UNCONFIRMED",
  TWILIO_CREATE_UNCERTAIN: "callFailures.TWILIO_CREATE_UNCONFIRMED",
  TWILIO_BUSY: "callFailures.TWILIO_BUSY",
  TWILIO_NO_ANSWER: "callFailures.TWILIO_NO_ANSWER",
  TWILIO_CANCELLED: "callFailures.TWILIO_CANCELLED",
  TWILIO_FAILED: "callFailures.TWILIO_FAILED",
  TWILIO_FALLBACK: "callFailures.TWILIO_FALLBACK",
  PREPARATION_FAILED: "callFailures.PREPARATION_FAILED",
  INVALID_PREPARATION_RESPONSE: "callFailures.RUNTIME_CONNECTION_ERROR",
  RUNTIME_TOKEN_DERIVATION_MISMATCH: "callFailures.RUNTIME_CONNECTION_ERROR",
  MISSING_RUNTIME_TOKEN: "callFailures.RUNTIME_CONNECTION_ERROR",
  RUNTIME_TOKEN_REJECTED: "callFailures.RUNTIME_CONNECTION_ERROR",
  PREPARED_SESSION_MISSING: "callFailures.RUNTIME_CONNECTION_ERROR",
  INTERVIEW_RUNTIME_NOT_READY: "callFailures.RUNTIME_CONNECTION_ERROR",
  INTERVIEW_RUNTIME_ERROR: "callFailures.INTERVIEW_RUNTIME_ERROR",
  INTERVIEW_RESULT_MISSING: "callFailures.INTERVIEW_RESULT_MISSING",
  MODEL_FAILURE_LIMIT: "callFailures.MODEL_FAILURE",
  TTS_FAILURE: "callFailures.TTS_FAILURE",
  STT_FAILURE: "callFailures.STT_FAILURE",
  STT_NOT_STARTED: "callFailures.TRANSCRIPTION_SERVICE_ERROR",
  MEDIA_SEND_FAILURE: "callFailures.MEDIA_STREAM_ERROR",
  INTERVIEW_SNAPSHOT_HASH_MISMATCH: "callFailures.INTERVIEW_SNAPSHOT_HASH_MISMATCH",
  INTERVIEW_PREPARATION_CONFLICT: "callFailures.INTERVIEW_PREPARATION_CONFLICT",
  INVALID_TWILIO_RESPONSE: "callFailures.CALL_SERVICE_ERROR",
  CALL_FAILED: "callFailures.TECHNICAL_ERROR",
} as const;

export type CallFailureMessageKey =
  | (typeof CALL_FAILURE_KEYS)[keyof typeof CALL_FAILURE_KEYS]
  | "callFailures.TWILIO_CALLBACK_ERROR"
  | "callFailures.CONSENT_AUDIO_ERROR"
  | "callFailures.MEDIA_STREAM_ERROR"
  | "callFailures.VOICE_SERVICE_ERROR"
  | "callFailures.TRANSCRIPTION_SERVICE_ERROR"
  | "callFailures.RUNTIME_CONNECTION_ERROR"
  | "callFailures.MODEL_FAILURE"
  | "callFailures.CALL_SERVICE_ERROR"
  | "callFailures.TECHNICAL_ERROR";

export type CallFailureTranslator = ((key: CallFailureMessageKey) => string) & {
  has?: (key: CallFailureMessageKey) => boolean;
};

const CALL_FAILURE_PATTERNS: ReadonlyArray<readonly [RegExp, CallFailureMessageKey]> = [
  [/^TWILIO_CREATE_REJECTED(?:_|$)/, "callFailures.TWILIO_CREATE_REJECTED"],
  [/^TWILIO_(?:CALLBACK|WEBHOOK)(?:_|$)/, "callFailures.TWILIO_CALLBACK_ERROR"],
  [/^CONSENT_(?:AUDIO|PROMPT)(?:_|$)/, "callFailures.CONSENT_AUDIO_ERROR"],
  [/^(?:TWILIO_)?MEDIA_STREAM(?:_|$)/, "callFailures.MEDIA_STREAM_ERROR"],
  [/^(?:CARTESIA_)?TTS(?:_|$)/, "callFailures.VOICE_SERVICE_ERROR"],
  [/^(?:DEEPGRAM_)?STT(?:_|$)/, "callFailures.TRANSCRIPTION_SERVICE_ERROR"],
  [/^(?:INTERVIEW_)?RUNTIME(?:_|$)/, "callFailures.RUNTIME_CONNECTION_ERROR"],
  [/^(?:EXECUTION_|CHECKPOINT_|LEASE_)/, "callFailures.RUNTIME_CONNECTION_ERROR"],
  [/^(?:TWILIO_|CALL_)/, "callFailures.CALL_SERVICE_ERROR"],
];

export function normalizeCallFailureCode(code: string): string {
  return code.trim().toUpperCase().replace(/[^A-Z0-9]+/g, "_").replace(/^_+|_+$/g, "");
}

export function resolveCallFailureKey(code: string): CallFailureMessageKey {
  const normalized = normalizeCallFailureCode(code);
  const direct = CALL_FAILURE_KEYS[normalized as keyof typeof CALL_FAILURE_KEYS];
  if (direct) return direct;
  return CALL_FAILURE_PATTERNS.find(([pattern]) => pattern.test(normalized))?.[1]
    ?? "callFailures.TECHNICAL_ERROR";
}

export function callFailureText(
  code: string,
  translate: CallFailureTranslator,
  locale: string,
  options: { showTechnicalCode?: boolean } = {},
): string {
  const normalized = normalizeCallFailureCode(code);
  const key = resolveCallFailureKey(normalized);
  const translated = !translate.has || translate.has(key) ? translate(key) : "";
  const fallback = locale.startsWith("vi")
    ? "Cuộc gọi gặp sự cố kỹ thuật. Vui lòng thử lại; nếu lỗi tiếp diễn, hãy liên hệ bộ phận hỗ trợ."
    : "The call encountered a technical problem. Please try again, and contact support if it continues.";
  const message = translated && translated !== key ? translated : fallback;
  const showTechnicalCode = options.showTechnicalCode ?? process.env.NODE_ENV === "development";
  return showTechnicalCode && key === "callFailures.TECHNICAL_ERROR" && normalized
    ? `${message} (${formatEnumLabel(normalized, locale)})`
    : message;
}
