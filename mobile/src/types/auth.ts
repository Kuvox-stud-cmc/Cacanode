export type AuthStatus =
  | 'bootstrapping'
  | 'unauthenticated'
  | 'awaiting_2fa'
  | 'authenticated';

export type AuthUser = {
  userId: string;
  tenantId: string;
  email: string;
  fullName: string;
  role: string;
  plan: 'STARTER' | 'TRIAL' | 'PRO' | 'BUSINESS' | 'ENTERPRISE';
};

export type MobileAuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer' | string;
  expiresIn: number;
  user: AuthUser;
};

export type LoginTwoFactorStep = {
  message: string;
  email: string;
  requires2FA: true;
};

export type MobileLoginResponse = MobileAuthResponse | LoginTwoFactorStep;

export type ResendTwoFactorResponse = {
  message: string;
  canRetryAfterSeconds?: number | null;
};

export function isLoginTwoFactorStep(value: MobileLoginResponse): value is LoginTwoFactorStep {
  return 'requires2FA' in value && value.requires2FA === true;
}
