import { configureStore } from '@reduxjs/toolkit';

import { aiApi, springApi } from '@/services/api/api';
import { authReducer } from '@/features/auth/store/auth-slice';

export function createAppStore() {
  return configureStore({
    reducer: {
      auth: authReducer,
      [springApi.reducerPath]: springApi.reducer,
      [aiApi.reducerPath]: aiApi.reducer,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(springApi.middleware, aiApi.middleware),
  });
}

export const store = createAppStore();

export type AppStore = ReturnType<typeof createAppStore>;
export type RootState = ReturnType<AppStore['getState']>;
export type AppDispatch = AppStore['dispatch'];
