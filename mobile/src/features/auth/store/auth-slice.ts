import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

import type { AuthStatus, AuthUser } from '@/types/auth';

export type AuthState = {
  status: AuthStatus;
  user: AuthUser | null;
  pendingTwoFactorEmail: string | null;
  bootstrapError: string | null;
};

const initialState: AuthState = {
  status: 'bootstrapping',
  user: null,
  pendingTwoFactorEmail: null,
  bootstrapError: null,
};

export const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    bootstrapStarted(state) {
      state.status = 'bootstrapping';
      state.user = null;
      state.pendingTwoFactorEmail = null;
      state.bootstrapError = null;
    },
    bootstrapFailed(state, action: PayloadAction<string>) {
      state.status = 'bootstrapping';
      state.user = null;
      state.pendingTwoFactorEmail = null;
      state.bootstrapError = action.payload;
    },
    authenticationRequired(state) {
      state.status = 'unauthenticated';
      state.user = null;
      state.pendingTwoFactorEmail = null;
      state.bootstrapError = null;
    },
    twoFactorRequired(state, action: PayloadAction<string>) {
      state.status = 'awaiting_2fa';
      state.user = null;
      state.pendingTwoFactorEmail = action.payload;
      state.bootstrapError = null;
    },
    sessionAuthenticated(state, action: PayloadAction<AuthUser>) {
      state.status = 'authenticated';
      state.user = action.payload;
      state.pendingTwoFactorEmail = null;
      state.bootstrapError = null;
    },
  },
});

export const {
  authenticationRequired,
  bootstrapFailed,
  bootstrapStarted,
  sessionAuthenticated,
  twoFactorRequired,
} = authSlice.actions;

export const authReducer = authSlice.reducer;
