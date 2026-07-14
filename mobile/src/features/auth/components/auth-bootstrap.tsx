import * as SplashScreen from 'expo-splash-screen';
import { type PropsWithChildren, useCallback, useEffect, useRef } from 'react';

import { ErrorState } from '@/components/feedback/error-state';
import { authApi } from '@/features/auth/api/auth-api';
import { bootstrapSession } from '@/services/auth/session-manager';
import { useAppDispatch, useAppSelector } from '@/store/hooks';

void SplashScreen.preventAutoHideAsync().catch(() => undefined);

export function AuthBootstrap({ children }: PropsWithChildren) {
  const dispatch = useAppDispatch();
  const { status, bootstrapError } = useAppSelector((state) => state.auth);
  const started = useRef(false);

  const bootstrap = useCallback(async () => {
    await bootstrapSession(dispatch, async (refreshToken) => {
      return dispatch(
        authApi.endpoints.refreshSession.initiate({ refreshToken }, { track: false }),
      ).unwrap();
    });
  }, [dispatch]);

  useEffect(() => {
    if (!started.current) {
      started.current = true;
      void bootstrap();
    }
  }, [bootstrap]);

  useEffect(() => {
    if (status !== 'bootstrapping' || bootstrapError) {
      void SplashScreen.hideAsync().catch(() => undefined);
    }
  }, [bootstrapError, status]);

  if (status === 'bootstrapping') {
    if (bootstrapError) {
      return (
        <ErrorState
          title="Unable to restore your session"
          description={bootstrapError}
          onRetry={() => void bootstrap()}
        />
      );
    }
    return null;
  }

  return children;
}
