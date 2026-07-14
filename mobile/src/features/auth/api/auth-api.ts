import { springApi } from '@/services/api/api';
import type {
  MobileAuthResponse,
  MobileLoginResponse,
  ResendTwoFactorResponse,
} from '@/types/auth';

export const authApi = springApi.injectEndpoints({
  endpoints: (build) => ({
    login: build.mutation<MobileLoginResponse, { email: string; password: string }>({
      query: (body) => ({ url: '/auth/mobile/login', method: 'POST', body }),
    }),
    verifyLoginTwoFactor: build.mutation<MobileAuthResponse, { email: string; code: string }>({
      query: (body) => ({ url: '/auth/mobile/verify-login-2fa', method: 'POST', body }),
    }),
    resendLoginTwoFactor: build.mutation<ResendTwoFactorResponse, { email: string }>({
      query: (body) => ({ url: '/auth/resend-login-2fa', method: 'POST', body }),
    }),
    refreshSession: build.mutation<MobileAuthResponse, { refreshToken: string }>({
      query: (body) => ({ url: '/auth/mobile/refresh', method: 'POST', body }),
    }),
    logoutSession: build.mutation<void, { refreshToken: string }>({
      query: (body) => ({ url: '/auth/mobile/logout', method: 'POST', body }),
    }),
  }),
});

export const {
  useLoginMutation,
  useLogoutSessionMutation,
  useResendLoginTwoFactorMutation,
  useVerifyLoginTwoFactorMutation,
} = authApi;
