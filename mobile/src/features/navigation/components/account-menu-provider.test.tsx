import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { Provider } from 'react-redux';

import { Button } from '@/components/ui/button';
import {
  AccountMenuProvider,
  useAccountMenu,
} from '@/features/navigation/components/account-menu-provider';
import { sessionAuthenticated } from '@/features/auth/store/auth-slice';
import { clearLocalSession } from '@/services/auth/session-manager';
import { tokenVault } from '@/services/auth/token-vault';
import { createAppStore } from '@/store';

const order: string[] = [];
const mockPush = jest.fn();
const mockReplace = jest.fn(() => order.push('route'));
const mockLogout = jest.fn(() => {
  order.push('server');
  return { unwrap: () => Promise.resolve() };
});

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
}));

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ bottom: 0, left: 0, right: 0, top: 0 }),
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

function OpenMenuButton() {
  const { open } = useAccountMenu();
  return <Button onPress={open}>Open menu</Button>;
}

async function renderMenu() {
  const store = createAppStore();
  store.dispatch(
    sessionAuthenticated({
      email: 'ada@example.com',
      fullName: 'Ada Lovelace',
      plan: 'PRO',
      role: 'TENANT_ADMIN',
      tenantId: 'tenant-1',
      userId: 'user-1',
    }),
  );
  return render(
    <Provider store={store}>
      <AccountMenuProvider>
        <OpenMenuButton />
      </AccountMenuProvider>
    </Provider>,
  );
}

describe('AccountMenuProvider', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    order.length = 0;
  });

  it('opens the identity sheet and navigates to settings', async () => {
    const screen = await renderMenu();
    await fireEvent.press(screen.getByRole('button', { name: 'Open menu' }));
    expect(screen.getByText('Ada Lovelace')).toBeTruthy();
    expect(screen.getByText('Tenant admin')).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Account settings' }));
    expect(mockPush).toHaveBeenCalledWith('/settings');
  });

  it('clears and redirects before best-effort server logout', async () => {
    jest.mocked(tokenVault.get).mockResolvedValue('refresh-token');
    jest.mocked(clearLocalSession).mockImplementation(async () => {
      order.push('clear');
    });
    const screen = await renderMenu();
    await fireEvent.press(screen.getByRole('button', { name: 'Open menu' }));
    await fireEvent.press(screen.getByRole('button', { name: 'Sign out' }));
    await waitFor(() => expect(mockLogout).toHaveBeenCalledWith({ refreshToken: 'refresh-token' }));
    expect(order).toEqual(['clear', 'route', 'server']);
  });
});
