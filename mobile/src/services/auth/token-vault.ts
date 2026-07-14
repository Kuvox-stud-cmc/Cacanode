import * as SecureStore from 'expo-secure-store';

const REFRESH_TOKEN_KEY = 'cacanode.auth.refresh-token.v1';

export const tokenVault = {
  get(): Promise<string | null> {
    return SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
  },
  set(refreshToken: string): Promise<void> {
    return SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken, {
      keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
      requireAuthentication: false,
    });
  },
  clear(): Promise<void> {
    return SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
  },
};
