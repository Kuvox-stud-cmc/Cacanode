import { render } from '@testing-library/react-native';
import * as ExpoRouter from 'expo-router';
import { Text } from 'react-native';
import { Provider } from 'react-redux';

import AppLayout from '@/app/(app)/_layout';
import AuthLayout from '@/app/(auth)/_layout';
import IndexRoute from '@/app/index';
import { AuthBootstrap } from '@/features/auth/components/auth-bootstrap';
import {
  authenticationRequired,
  sessionAuthenticated,
  twoFactorRequired,
} from '@/features/auth/store/auth-slice';
import { tokenVault } from '@/services/auth/token-vault';
import { createAppStore } from '@/store';
import type { AuthUser } from '@/types/auth';

jest.mock('expo-router', () => ({
  Redirect: jest.fn(() => null),
  Stack: jest.fn(() => null),
}));

jest.mock('expo-splash-screen', () => ({
  preventAutoHideAsync: jest.fn().mockResolvedValue(undefined),
  hideAsync: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('@/services/auth/token-vault', () => ({
  tokenVault: { get: jest.fn(), set: jest.fn(), clear: jest.fn() },
}));

const user = {
  userId: 'user-1',
  tenantId: 'tenant-1',
  email: 'person@example.com',
  fullName: 'Person Name',
  role: 'TENANT_ADMIN',
  plan: 'PRO',
} satisfies AuthUser;

async function renderWithStore(element: React.ReactElement, store = createAppStore()) {
  const screen = await render(<Provider store={store}>{element}</Provider>);
  return { ...screen, store };
}

describe('authentication routing', () => {
  beforeEach(() => jest.clearAllMocks());

  it('routes resolved auth states to login, verification, or the app', async () => {
    const unauthenticated = createAppStore();
    unauthenticated.dispatch(authenticationRequired());
    await renderWithStore(<IndexRoute />, unauthenticated);
    expect(jest.mocked(ExpoRouter.Redirect).mock.lastCall?.[0]).toEqual(
      expect.objectContaining({ href: '/login' }),
    );

    const awaiting = createAppStore();
    awaiting.dispatch(twoFactorRequired(user.email));
    await renderWithStore(<IndexRoute />, awaiting);
    expect(jest.mocked(ExpoRouter.Redirect).mock.lastCall?.[0]).toEqual(
      expect.objectContaining({ href: '/verify-login-2fa' }),
    );

    const authenticated = createAppStore();
    authenticated.dispatch(sessionAuthenticated(user));
    await renderWithStore(<IndexRoute />, authenticated);
    expect(jest.mocked(ExpoRouter.Redirect).mock.lastCall?.[0]).toEqual(
      expect.objectContaining({ href: '/dashboard' }),
    );
  });

  it('protects the app group and redirects authenticated users away from auth routes', async () => {
    const unauthenticated = createAppStore();
    unauthenticated.dispatch(authenticationRequired());
    await renderWithStore(<AppLayout />, unauthenticated);
    expect(jest.mocked(ExpoRouter.Redirect).mock.lastCall?.[0]).toEqual(
      expect.objectContaining({ href: '/login' }),
    );

    const authenticated = createAppStore();
    authenticated.dispatch(sessionAuthenticated(user));
    await renderWithStore(<AuthLayout />, authenticated);
    expect(jest.mocked(ExpoRouter.Redirect).mock.lastCall?.[0]).toEqual(
      expect.objectContaining({ href: '/dashboard' }),
    );
  });

  it('renders no protected children while bootstrap is unresolved', async () => {
    jest.mocked(tokenVault.get).mockReturnValue(new Promise(() => undefined));
    const screen = await renderWithStore(
      <AuthBootstrap>
        <Text>Protected content</Text>
      </AuthBootstrap>,
    );
    expect(screen.queryByText('Protected content')).toBeNull();
  });
});
