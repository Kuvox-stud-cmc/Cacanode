import { render } from '@testing-library/react-native';
import * as ExpoRouter from 'expo-router';
import { Provider } from 'react-redux';

import AppLayout from '@/app/(app)/_layout';
import AppTabsLayout from '@/app/(app)/(tabs)/_layout';
import { sessionAuthenticated } from '@/features/auth/store/auth-slice';
import { createAppStore } from '@/store';

jest.mock('expo-router', () => {
  const Stack = Object.assign(jest.fn((props: { children?: React.ReactNode }) => props.children), {
    Screen: jest.fn(() => null),
  });
  const Tabs = Object.assign(jest.fn((props: { children?: React.ReactNode }) => props.children), {
    Screen: jest.fn(() => null),
  });
  return { Redirect: jest.fn(() => null), Stack, Tabs, useRouter: () => ({ push: jest.fn(), replace: jest.fn() }) };
});

jest.mock('expo-symbols', () => ({ SymbolView: () => null }));
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ bottom: 0, left: 0, right: 0, top: 0 }),
}));
jest.mock('@/features/auth/api/auth-api', () => ({
  useLogoutSessionMutation: () => [jest.fn(), { isLoading: false }],
}));

function storeFor(role: string) {
  const store = createAppStore();
  store.dispatch(sessionAuthenticated({
    email: 'person@example.com', fullName: 'Person Name', plan: 'PRO', role,
    tenantId: 'tenant-1', userId: 'user-1',
  }));
  return store;
}

describe('authenticated navigation', () => {
  beforeEach(() => jest.clearAllMocks());

  it('configures native gestures and every secondary stack route', async () => {
    await render(<Provider store={storeFor('USER')}><AppLayout /></Provider>);
    expect(jest.mocked(ExpoRouter.Stack)).toHaveBeenCalledWith(
      expect.objectContaining({
        screenOptions: expect.objectContaining({ gestureEnabled: true, fullScreenGestureEnabled: true }),
      }),
      undefined,
    );
    const names = jest.mocked(ExpoRouter.Stack.Screen).mock.calls.map(([props]) => props.name);
    expect(names).toEqual(expect.arrayContaining([
      '(tabs)', 'conversations/index', 'conversations/[conversationId]', 'documents/upload',
      'documents/[documentId]', 'tickets/[ticketId]', 'settings/index',
    ]));
  });

  it.each(['USER', 'TENANT_ADMIN'])('shows all primary tabs to %s', async (role) => {
    await render(<Provider store={storeFor(role)}><AppTabsLayout /></Provider>);
    const screens = jest.mocked(ExpoRouter.Tabs.Screen).mock.calls.map(([props]) => props) as {
      name: string;
      options: { href?: unknown };
    }[];
    expect(screens.map((props) => props.name)).toEqual(['dashboard', 'chat', 'documents', 'tickets']);
    expect(screens.every((props) => props.options.href !== null)).toBe(true);
  });
});
