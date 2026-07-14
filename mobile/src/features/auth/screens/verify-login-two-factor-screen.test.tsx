import { fireEvent, render, waitFor } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { Provider } from 'react-redux';

import { twoFactorRequired } from '@/features/auth/store/auth-slice';
import { VerifyLoginTwoFactorScreen } from '@/features/auth/screens/verify-login-two-factor-screen';
import { createAppStore } from '@/store';

const mockReplace = jest.fn();
const mockVerify = jest.fn();
const mockResend = jest.fn();

jest.mock('expo-router', () => ({
  Redirect: () => null,
  useRouter: () => ({ replace: mockReplace }),
}));

jest.mock('@/components/layout/screen', () => ({
  KeyboardScreen: ({ children }: { children: ReactNode }) => children,
}));

jest.mock('@/features/auth/api/auth-api', () => ({
  useVerifyLoginTwoFactorMutation: () => [mockVerify, { isLoading: false }],
  useResendLoginTwoFactorMutation: () => [mockResend, { isLoading: false }],
}));

jest.mock('@/services/auth/session-manager', () => ({
  commitSession: jest.fn().mockResolvedValue(undefined),
}));

async function renderScreen() {
  const store = createAppStore();
  store.dispatch(twoFactorRequired('person@example.com'));
  return await render(
    <Provider store={store}>
      <VerifyLoginTwoFactorScreen />
    </Provider>,
  );
}

describe('VerifyLoginTwoFactorScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('accepts exactly six numeric digits and verifies with the pending email', async () => {
    mockVerify.mockReturnValue({
      unwrap: () => Promise.resolve({
        accessToken: 'access',
        refreshToken: 'refresh',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: {},
      }),
    });
    const screen = await renderScreen();
    const input = screen.getByLabelText('Confirmation code');
    await fireEvent.changeText(input, '12a34567');
    expect(screen.getByLabelText('Confirmation code').props.value).toBe('123456');
    await fireEvent.press(screen.getByRole('button', { name: 'Verify and continue' }));

    await waitFor(() => {
      expect(mockVerify).toHaveBeenCalledWith({ email: 'person@example.com', code: '123456' });
      expect(mockReplace).toHaveBeenCalledWith('/dashboard');
    });
  });

  it('uses the server cooldown when resending', async () => {
    mockResend.mockReturnValue({
      unwrap: () => Promise.resolve({ message: 'Wait before resending', canRetryAfterSeconds: 12 }),
    });
    const screen = await renderScreen();
    await fireEvent.press(screen.getByRole('button', { name: 'Resend confirmation code' }));

    expect(await screen.findByText('Resend in 12s')).toBeTruthy();
    expect(screen.getByText('Wait before resending')).toBeTruthy();
  });

  it('reports invalid and expired codes generically', async () => {
    mockVerify.mockReturnValue({
      unwrap: () => Promise.reject({ kind: 'http', status: 401, message: 'server details' }),
    });
    const screen = await renderScreen();
    await fireEvent.changeText(screen.getByLabelText('Confirmation code'), '123456');
    await fireEvent.press(screen.getByRole('button', { name: 'Verify and continue' }));

    expect(
      await screen.findByText('That code is invalid or expired. Request a new code and try again.'),
    ).toBeTruthy();
    expect(screen.queryByText('server details')).toBeNull();
  });
});
