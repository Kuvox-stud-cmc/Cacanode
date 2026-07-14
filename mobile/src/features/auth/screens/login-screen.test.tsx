import { fireEvent, render, waitFor } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { Provider } from 'react-redux';

import { LoginScreen } from '@/features/auth/screens/login-screen';
import { createAppStore } from '@/store';

const mockReplace = jest.fn();
const mockLogin = jest.fn();

jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

jest.mock('@/components/layout/screen', () => ({
  KeyboardScreen: ({ children }: { children: ReactNode }) => children,
}));

jest.mock('@/features/auth/api/auth-api', () => ({
  useLoginMutation: () => [mockLogin, { isLoading: false }],
}));

jest.mock('@/services/auth/session-manager', () => ({
  commitSession: jest.fn().mockResolvedValue(undefined),
}));

async function renderScreen() {
  const store = createAppStore();
  const screen = await render(
    <Provider store={store}>
      <LoginScreen />
    </Provider>,
  );
  return { ...screen, store };
}

describe('LoginScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('validates email and the eight-character password requirement', async () => {
    const screen = await renderScreen();
    await fireEvent.changeText(screen.getByLabelText('Email'), 'not-an-email');
    await fireEvent.changeText(screen.getByLabelText('Password'), 'short');
    await fireEvent.press(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Enter a valid email address.')).toBeTruthy();
    expect(screen.getByText('Password must contain at least 8 characters.')).toBeTruthy();
    expect(mockLogin).not.toHaveBeenCalled();
  });

  it('toggles password visibility and enters the pending 2FA state', async () => {
    mockLogin.mockReturnValue({
      unwrap: () => Promise.resolve({
        requires2FA: true,
        email: 'person@example.com',
        message: 'Check your email',
      }),
    });
    const screen = await renderScreen();
    const password = screen.getByLabelText('Password');
    expect(password.props.secureTextEntry).toBe(true);
    await fireEvent.press(screen.getByRole('button', { name: 'Show password' }));
    expect(screen.getByLabelText('Password').props.secureTextEntry).toBe(false);

    await fireEvent.changeText(screen.getByLabelText('Email'), 'person@example.com');
    await fireEvent.changeText(screen.getByLabelText('Password'), 'password123');
    await fireEvent.press(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/verify-login-2fa'));
    expect(screen.store.getState().auth).toMatchObject({
      status: 'awaiting_2fa',
      pendingTwoFactorEmail: 'person@example.com',
    });
  });
});
