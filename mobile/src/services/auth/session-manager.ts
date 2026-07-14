import type { Dispatch, UnknownAction } from '@reduxjs/toolkit';

import {
  authenticationRequired,
  bootstrapFailed,
  bootstrapStarted,
  sessionAuthenticated,
} from '@/features/auth/store/auth-slice';
import type { ApiError } from '@/services/api/errors';
import { accessTokenStore } from '@/services/auth/access-token-store';
import { tokenVault } from '@/services/auth/token-vault';
import type { MobileAuthResponse } from '@/types/auth';

type AppDispatchLike = Dispatch<UnknownAction>;

const resetApiCaches = (dispatch: AppDispatchLike) => {
  dispatch({ type: 'springApi/resetApiState' });
  dispatch({ type: 'aiApi/resetApiState' });
};

export async function commitSession(
  credentials: MobileAuthResponse,
  dispatch: AppDispatchLike,
): Promise<void> {
  try {
    await tokenVault.set(credentials.refreshToken);
  } catch (error) {
    accessTokenStore.set(null);
    await tokenVault.clear().catch(() => undefined);
    dispatch(authenticationRequired());
    resetApiCaches(dispatch);
    throw error;
  }

  accessTokenStore.set(credentials.accessToken);
  dispatch(sessionAuthenticated(credentials.user));
}

export async function clearLocalSession(dispatch: AppDispatchLike): Promise<void> {
  accessTokenStore.set(null);
  dispatch(authenticationRequired());
  resetApiCaches(dispatch);
  await tokenVault.clear().catch(() => undefined);
}

export type RefreshCredentials = (refreshToken: string) => Promise<MobileAuthResponse>;

export async function bootstrapSession(
  dispatch: AppDispatchLike,
  refreshCredentials: RefreshCredentials,
): Promise<void> {
  dispatch(bootstrapStarted());

  let refreshToken: string | null;
  try {
    refreshToken = await tokenVault.get();
  } catch {
    dispatch(bootstrapFailed('Secure session storage is unavailable. Please try again.'));
    return;
  }

  if (!refreshToken) {
    accessTokenStore.set(null);
    dispatch(authenticationRequired());
    return;
  }

  try {
    const credentials = await refreshCredentials(refreshToken);
    await commitSession(credentials, dispatch);
  } catch (error) {
    const apiError = error as Partial<ApiError>;
    if (apiError.kind === 'http' && apiError.status === 401) {
      await clearLocalSession(dispatch);
      return;
    }

    if (apiError.kind === 'network' || apiError.kind === 'timeout') {
      accessTokenStore.set(null);
      dispatch(bootstrapFailed(apiError.message ?? 'Unable to restore your session.'));
      return;
    }

    await clearLocalSession(dispatch);
  }
}
