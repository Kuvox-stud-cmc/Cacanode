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
const mockOpenBilling = jest.fn(() => Promise.resolve());
let mockBillingAccount: import('@/features/billing/types').BillingAccount | undefined;
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

jest.mock('@/features/billing/api/billing-api', () => ({
  useGetBillingAccountQuery: () => ({ data: mockBillingAccount }),
}));

jest.mock('@/features/billing/services/billing-web-link', () => ({
  openBillingManagement: () => mockOpenBilling(),
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
    mockBillingAccount = undefined;
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

  it('opens web billing for tenant admins', async () => {
    const store = createAppStore();
    store.dispatch(sessionAuthenticated({
      userId: 'user-1',
      tenantId: 'tenant-1',
      email: 'person@example.com',
      fullName: 'Person Name',
      role: 'TENANT_ADMIN',
      plan: 'TRIAL',
    }));
    const screen = await render(
      <Provider store={store}>
        <AccountScreen />
      </Provider>,
    );

    expect(screen.getByText('Trial')).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Manage billing on web' }));
    await waitFor(() => expect(mockOpenBilling).toHaveBeenCalledTimes(1));
  });

  it('renders Business grace and reserved hiring usage read-only', async () => {
    mockBillingAccount = {
      planCode: 'BUSINESS', status: 'GRACE', interval: 'MONTHLY', trialEndsAt: null,
      paidThroughAt: '2026-08-01T00:00:00', graceEndsAt: '2026-08-04T00:00:00',
      quotaPeriodStart: '2026-07-01T00:00:00', nextQuotaResetAt: '2026-08-01T00:00:00',
      messages: { used: 1, limit: 50000, overLimit: false },
      documents: { used: 1, limit: 250, overLimit: false },
      teamMembers: { used: 2, limit: 15, overLimit: false },
      storageMb: { used: 20, limit: 51200, overLimit: false },
      activeJobs: { used: 0, reserved: 2, limit: 10, overLimit: false },
      verifiedApplications: { used: 8, reserved: 0, limit: 1000, overLimit: false },
      interviewSeconds: { used: 600, reserved: 120, limit: 18000, overLimit: false },
      cvAnalyses: { used: 3, reserved: 0, limit: 500, overLimit: false },
      recruitmentStorageBytes: { used: 1048576, reserved: 2097152, limit: 10737418240, overLimit: false },
      features: { apiAccess: true, webhooks: true, advancedAnalytics: true, customBranding: true },
      pendingPayment: null, cancelAtPeriodEnd: false,
    };
    const store = createAppStore();
    store.dispatch(sessionAuthenticated({
      userId: 'user-1', tenantId: 'tenant-1', email: 'person@example.com', fullName: 'Person Name',
      role: 'TENANT_ADMIN', plan: 'BUSINESS',
    }));

    const screen = await render(<Provider store={store}><AccountScreen /></Provider>);

    expect(screen.getByText('Business · Grace')).toBeTruthy();
    expect(screen.getByText('Hiring usage')).toBeTruthy();
    expect(screen.getByText('2 min reserved')).toBeTruthy();
    expect(screen.getByText('2 MB reserved')).toBeTruthy();
  });
});
