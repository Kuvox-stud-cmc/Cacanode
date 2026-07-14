import {
  fetchBaseQuery,
  type BaseQueryFn,
  type FetchArgs,
  type FetchBaseQueryError,
} from '@reduxjs/toolkit/query/react';
import { Mutex } from 'async-mutex';

import { env } from '@/constants/env';
import { normalizeApiError, type ApiError } from '@/services/api/errors';
import { accessTokenStore } from '@/services/auth/access-token-store';
import { clearLocalSession, commitSession } from '@/services/auth/session-manager';
import { tokenVault } from '@/services/auth/token-vault';
import type { MobileAuthResponse } from '@/types/auth';

const refreshMutex = new Mutex();
let lastRefreshError: ApiError | null = null;
let refreshGeneration = 0;
const requestTimeout = process.env.NODE_ENV === 'test' ? undefined : 15_000;

function requestUrl(args: string | FetchArgs): string {
  return typeof args === 'string' ? args : args.url;
}

function isAuthenticationRequest(args: string | FetchArgs): boolean {
  return requestUrl(args).includes('/auth/');
}

function isUnauthorized(error: FetchBaseQueryError | undefined): boolean {
  return error?.status === 401;
}

const refreshBaseQuery = fetchBaseQuery({
  baseUrl: env.apiBaseUrl,
  timeout: requestTimeout,
  prepareHeaders(headers) {
    headers.set('Accept', 'application/json');
    return headers;
  },
});

async function refreshAccessToken(
  api: Parameters<typeof refreshBaseQuery>[1],
  extraOptions: Parameters<typeof refreshBaseQuery>[2],
): Promise<ApiError | null> {
  const refreshToken = await tokenVault.get().catch(() => null);
  if (!refreshToken) {
    await clearLocalSession(api.dispatch);
    return normalizeApiError({ status: 401, data: { message: 'Authentication required' } });
  }

  const result = await refreshBaseQuery(
    { url: '/auth/mobile/refresh', method: 'POST', body: { refreshToken } },
    api,
    extraOptions,
  );

  if (result.data) {
    try {
      await commitSession(result.data as MobileAuthResponse, api.dispatch);
      return null;
    } catch {
      return normalizeApiError({
        status: 'CUSTOM_ERROR',
        error: 'Secure session persistence failed',
      });
    }
  }

  const error = normalizeApiError(result.error as FetchBaseQueryError);
  if (error.status === 401) {
    await clearLocalSession(api.dispatch);
  }
  return error;
}

export function createApiBaseQuery(
  baseUrl: string,
): BaseQueryFn<string | FetchArgs, unknown, ApiError> {
  const rawBaseQuery = fetchBaseQuery({
    baseUrl,
    timeout: requestTimeout,
    prepareHeaders(headers) {
      headers.set('Accept', 'application/json');
      const accessToken = accessTokenStore.get();
      if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`);
      }
      return headers;
    },
  });

  return async (args, api, extraOptions) => {
    const originalAccessToken = accessTokenStore.get();
    const requestRefreshGeneration = refreshGeneration;
    let result = await rawBaseQuery(args, api, extraOptions);

    if (isUnauthorized(result.error) && !isAuthenticationRequest(args)) {
      if (refreshGeneration !== requestRefreshGeneration) {
        if (lastRefreshError) {
          return { error: lastRefreshError };
        }
        if (accessTokenStore.get()) {
          result = await rawBaseQuery(args, api, extraOptions);
        }
      } else if (refreshMutex.isLocked()) {
        await refreshMutex.waitForUnlock();

        if (lastRefreshError) {
          return { error: lastRefreshError };
        }

        if (accessTokenStore.get() && accessTokenStore.get() !== originalAccessToken) {
          result = await rawBaseQuery(args, api, extraOptions);
        }
      } else {
        const release = await refreshMutex.acquire();
        try {
          lastRefreshError = await refreshAccessToken(api, extraOptions);
          refreshGeneration += 1;
        } finally {
          release();
        }

        if (lastRefreshError) {
          return { error: lastRefreshError };
        }

        result = await rawBaseQuery(args, api, extraOptions);
      }

      if (isUnauthorized(result.error)) {
        await clearLocalSession(api.dispatch);
      }
    }

    if (result.error) {
      return { error: normalizeApiError(result.error as FetchBaseQueryError) };
    }

    return { data: result.data };
  };
}
