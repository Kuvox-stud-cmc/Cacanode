import * as SecureStore from 'expo-secure-store';

import { tokenVault } from '@/services/auth/token-vault';

jest.mock('expo-secure-store', () => ({
  AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY: 'device-only',
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

describe('token vault', () => {
  beforeEach(() => jest.clearAllMocks());

  it('uses one versioned device-only key without biometrics', async () => {
    jest.mocked(SecureStore.getItemAsync).mockResolvedValue('refresh-token');
    await expect(tokenVault.get()).resolves.toBe('refresh-token');
    await tokenVault.set('rotated-token');
    await tokenVault.clear();

    expect(SecureStore.setItemAsync).toHaveBeenCalledWith(
      'cacanode.auth.refresh-token.v1',
      'rotated-token',
      { keychainAccessible: 'device-only', requireAuthentication: false },
    );
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('cacanode.auth.refresh-token.v1');
  });
});
