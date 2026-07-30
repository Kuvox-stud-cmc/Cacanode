import { fireEvent, render } from '@testing-library/react-native';
import type { ReactElement, ReactNode } from 'react';
import { Provider } from 'react-redux';

import { DashboardScreen } from '@/features/dashboard/screens/dashboard-screen';
import type { DashboardSummary } from '@/features/dashboard/types';
import { sessionAuthenticated } from '@/features/auth/store/auth-slice';
import { createAppStore } from '@/store';
import i18n from '@/i18n';

const mockPush = jest.fn();
const mockRefetch = jest.fn();
const mockUseDashboard = jest.fn();

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush }),
}));

jest.mock('@/components/layout/screen', () => {
  const { Pressable, Text, View } = jest.requireActual<typeof import('react-native')>('react-native');
  return {
    ScrollScreen: ({
      children,
      refreshControl,
    }: {
      children: ReactNode;
      refreshControl?: ReactElement<{ onRefresh?: () => void }>;
    }) => (
      <View>
        {refreshControl ? (
          <Pressable accessibilityLabel="Pull to refresh dashboard" onPress={refreshControl.props.onRefresh}>
            <Text>Refresh dashboard</Text>
          </Pressable>
        ) : null}
        {children}
      </View>
    ),
  };
});

jest.mock('@/components/ui/skeleton', () => {
  const { Text } = jest.requireActual<typeof import('react-native')>('react-native');
  return { Skeleton: () => <Text>Skeleton</Text> };
});

jest.mock('@/features/dashboard/api/dashboard-api', () => ({
  useGetDashboardSummaryQuery: () => mockUseDashboard(),
}));

const summary: DashboardSummary = {
  activeUsers: 7,
  activeUsersAddedThisWeek: 1,
  documentsAddedThisWeek: 2,
  recentDocuments: [
    {
      fileName: 'A very long quarterly knowledge base document name that must remain readable.pdf',
      fileSizeBytes: 2048,
      fileType: 'PDF',
      id: 'document-1',
      status: 'COMPLETED',
      uploadedAt: '2026-07-14T08:30:00',
    },
  ],
  storageLimitBytes: 10 * 1024 * 1024,
  storedDocumentBytes: 1024 * 1024,
  totalDocuments: 9,
  userMessagesPreviousMonth: 10,
  userMessagesThisMonth: 12,
};

function storeFor(role = 'USER') {
  const store = createAppStore();
  store.dispatch(sessionAuthenticated({
    email: 'ada@example.com',
    fullName: 'Ada Lovelace',
    plan: 'PRO',
    role,
    tenantId: 'tenant-1',
    userId: 'user-1',
  }));
  return store;
}

async function renderDashboard(role = 'USER') {
  return render(
    <Provider store={storeFor(role)}>
      <DashboardScreen />
    </Provider>,
  );
}

describe('DashboardScreen', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en');
    jest.clearAllMocks();
    mockUseDashboard.mockReturnValue({
      data: summary,
      error: undefined,
      isFetching: false,
      isLoading: false,
      refetch: mockRefetch,
    });
  });

  it('renders dashboard metrics and shortcuts in Vietnamese', async () => {
    await i18n.changeLanguage('vi');
    const screen = await renderDashboard();
    expect(screen.getByText('Chào mừng, Ada')).toBeTruthy();
    expect(screen.getByText('Tổng tài liệu')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Trò chuyện' })).toBeTruthy();
    expect(screen.queryByText('Total documents')).toBeNull();
  });

  it('renders tenant metrics, plan, document state, and long flexible text', async () => {
    const screen = await renderDashboard();
    expect(screen.getByText('Welcome, Ada')).toBeTruthy();
    expect(screen.getByText('PRO')).toBeTruthy();
    expect(screen.getByText('Total documents')).toBeTruthy();
    expect(screen.getByText('Messages this month')).toBeTruthy();
    expect(screen.getByText('Completed')).toBeTruthy();
    const longName = screen.getByText(summary.recentDocuments[0].fileName);
    expect(longName.props.numberOfLines).toBeUndefined();
  });

  it.each(['USER', 'TENANT_ADMIN'])('shows every permitted shortcut to %s', async (role) => {
    const screen = await renderDashboard(role);
    for (const title of ['Chat', 'Upload document', 'Conversations', 'Tickets']) {
      expect(screen.getByRole('button', { name: title })).toBeTruthy();
    }
  });

  it('refreshes from the native pull control and retries stale data', async () => {
    mockUseDashboard.mockReturnValue({
      data: summary,
      error: { kind: 'network', message: 'Check your connection.' },
      isFetching: false,
      isLoading: false,
      refetch: mockRefetch,
    });
    const screen = await renderDashboard();
    await fireEvent.press(screen.getByLabelText('Pull to refresh dashboard'));
    await fireEvent.press(screen.getByRole('button', { name: 'Try again' }));
    expect(mockRefetch).toHaveBeenCalledTimes(2);
    expect(screen.getByText('Dashboard may be out of date')).toBeTruthy();
    expect(screen.getByText('Total documents')).toBeTruthy();
  });

  it('renders initial loading and full retry states', async () => {
    mockUseDashboard.mockReturnValue({
      data: undefined,
      error: undefined,
      isFetching: true,
      isLoading: true,
      refetch: mockRefetch,
    });
    const loading = await renderDashboard();
    expect(loading.getByLabelText('Loading dashboard')).toBeTruthy();
    await loading.unmount();

    mockUseDashboard.mockReturnValue({
      data: undefined,
      error: { kind: 'http', message: 'Service unavailable.' },
      isFetching: false,
      isLoading: false,
      refetch: mockRefetch,
    });
    const failed = await renderDashboard();
    expect(failed.getByText('Unable to load your dashboard')).toBeTruthy();
    expect(failed.getByText('Dashboard data is temporarily unavailable. Please try again.')).toBeTruthy();
    expect(failed.queryByText('Service unavailable.')).toBeNull();
    await fireEvent.press(failed.getByRole('button', { name: 'Try again' }));
    expect(mockRefetch).toHaveBeenCalledTimes(1);
  });

  it('opens shortcut, document detail, and empty upload routes', async () => {
    const screen = await renderDashboard();
    await fireEvent.press(screen.getByRole('button', { name: 'Chat' }));
    await fireEvent.press(screen.getByRole('button', { name: summary.recentDocuments[0].fileName }));
    expect(mockPush).toHaveBeenNthCalledWith(1, '/chat');
    expect(mockPush).toHaveBeenNthCalledWith(2, {
      pathname: '/documents/[documentId]',
      params: { documentId: 'document-1' },
    });

    mockUseDashboard.mockReturnValue({
      data: { ...summary, recentDocuments: [] },
      error: undefined,
      isFetching: false,
      isLoading: false,
      refetch: mockRefetch,
    });
    const empty = await renderDashboard();
    expect(empty.getByText('No documents uploaded yet')).toBeTruthy();
    await fireEvent.press(empty.getByRole('button', { name: 'Upload first document' }));
    expect(mockPush).toHaveBeenLastCalledWith('/documents/upload');
  });
});
