import type {
  AuthResponse,
  LoginResponse,
  RegisterResponse,
  ResendVerificationResponse,
} from "@/types";

export function getApiBase(): string {
  const base = process.env.NEXT_PUBLIC_API_URL;
  if (!base) {
    throw new Error("NEXT_PUBLIC_API_URL is not set");
  }
  return base.replace(/\/$/, "");
}

type ApiErrorBody = {
  message?: string | string[];
};

function parseErrorMessage(body: unknown): string {
  if (!body || typeof body !== "object") return "Something went wrong";
  const msg = (body as ApiErrorBody).message;
  if (typeof msg === "string") return msg;
  if (Array.isArray(msg)) return msg.join(" ");
  return "Something went wrong";
}

async function parseJsonSafe(res: Response): Promise<unknown> {
  try {
    return await res.json();
  } catch {
    return null;
  }
}

let refreshInFlight: Promise<AuthResponse> | null = null;

export async function refreshApi(): Promise<AuthResponse> {
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    const res = await fetch(`${getApiBase()}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
    });
    const body = await parseJsonSafe(res);
    if (!res.ok) {
      throw new Error(parseErrorMessage(body));
    }
    return body as AuthResponse;
  })().finally(() => {
    refreshInFlight = null;
  });

  return refreshInFlight;
}

export async function loginApi(payload: {
  email: string;
  password: string;
  rememberMe: boolean;
}): Promise<LoginResponse> {
  const res = await fetch(`${getApiBase()}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  const body = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(parseErrorMessage(body));
  }
  return body as LoginResponse;
}

export async function verifyLogin2FAApi(token: string): Promise<AuthResponse> {
  const res = await fetch(`${getApiBase()}/api/auth/verify-login-2fa`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ token }),
  });
  const body = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(parseErrorMessage(body));
  }
  return body as AuthResponse;
}

export async function resendLogin2FAApi(
  email: string,
): Promise<ResendVerificationResponse> {
  const res = await fetch(`${getApiBase()}/api/auth/resend-login-2fa`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ email }),
  });
  const body = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(parseErrorMessage(body));
  }
  return body as ResendVerificationResponse;
}

export async function registerApi(payload: {
  companyName: string;
  fullName: string;
  email: string;
  password: string;
}): Promise<RegisterResponse> {
  const res = await fetch(`${getApiBase()}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  const body = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(parseErrorMessage(body));
  }
  return body as RegisterResponse;
}

export async function verifyEmailApi(token: string): Promise<AuthResponse> {
  const res = await fetch(`${getApiBase()}/api/auth/verify-email`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ token }),
  });
  const body = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(parseErrorMessage(body));
  }
  return body as AuthResponse;
}

export async function resendVerificationApi(
  email: string,
): Promise<ResendVerificationResponse> {
  const res = await fetch(`${getApiBase()}/api/auth/resend-verification`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ email }),
  });
  const body = await parseJsonSafe(res);
  if (!res.ok) {
    throw new Error(parseErrorMessage(body));
  }
  return body as ResendVerificationResponse;
}

export async function logoutApi(): Promise<void> {
  const res = await fetch(`${getApiBase()}/api/auth/logout`, {
    method: "POST",
    credentials: "include",
  });
  if (!res.ok && res.status !== 204) {
    const body = await parseJsonSafe(res);
    throw new Error(parseErrorMessage(body));
  }
}
