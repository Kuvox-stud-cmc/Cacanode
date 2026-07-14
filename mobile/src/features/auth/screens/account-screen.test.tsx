import { fireEvent, render, waitFor } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { Provider } from 'react-redux';

import { sessionAuthenticated } from '@/features/auth/store/auth-slice';
import { AccountScreen } from '@/features/auth/screens/account-screen';
import { clearLocalSession } from '@/services/auth/session-manager';
import { tokenVault } from '@/services/auth/token-vault';
import { createAppStore } from '@/store';

const order: string[] = [];
const mockReplace = jest.fn(() => order.push('route'));
const mockLogout = jest.fn(() => {
  order.push('server');
  return { unwrap: () => Promise.reject(new Error('offline')) };
});

jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

jest.mock('@/components/layout/screen', () => ({
  ScrollScreen: ({ children }: { children: ReactNode }) => children,
}));

jest.mock('@/features/auth/api/auth-api', () => ({
  useLogoutSessionMutation: () => [mockLogout, { isLoading: false }],
}));

jest.mock('@/services/auth/token-vault', () => ({
  tokenVault: { get: jest.fn() },
}));

jest.mock('@/services/auth/session-manager', () => ({
  clearLocalSession: jest.fn(),
}));

describe('AccountScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    order.length = 0;
  });

  it('clears and routes locally before attempting best-effort server logout', async () => {
    jest.mocked(tokenVault.get).mockResolvedValue('refresh-token');
    jest.mocked(clearLocalSession).mockImplementation(async () => {
      order.push('clear');
    });
    const store = createAppStore();
    store.dispatch(sessionAuthenticated({
      userId: 'user-1',
      tenantId: 'tenant-1',
      email: 'person@example.com',
      fullName: 'Person Name',
      role: 'TENANT_ADMIN',
      plan: 'PRO',
    }));
    const screen = await render(
      <Provider store={store}>
        <AccountScreen />
      </Provider>,
    );

    await fireEvent.press(screen.getByRole('button', { name: 'Sign out' }));
    await waitFor(() => expect(mockLogout).toHaveBeenCalledWith({ refreshToken: 'refresh-token' }));

    expect(order).toEqual(['clear', 'route', 'server']);
    expect(mockReplace).toHaveBeenCalledWith('/login');
  });
});
