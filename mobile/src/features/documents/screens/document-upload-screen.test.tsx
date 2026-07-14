import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { Provider } from 'react-redux';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { sessionAuthenticated } from '@/features/auth/store/auth-slice';
import { DocumentUploadScreen } from '@/features/documents/screens/document-upload-screen';
import { createAppStore } from '@/store';

const mockPicker = jest.fn();
const mockUpload = jest.fn();

jest.mock('expo-document-picker', () => ({
  getDocumentAsync: (...args: unknown[]) => mockPicker(...args),
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({ status: 'FAILED' }),
}));

jest.mock('@/features/chat/api/workspace-api', () => ({
  useGetTenantWorkspaceQuery: () => ({
    data: { knowledgeBase: { id: 'kb-1' } }, isLoading: false, isError: false, refetch: jest.fn(),
  }),
}));

jest.mock('@/features/documents/api/documents-api', () => ({
  useUploadDocumentMutation: () => [mockUpload],
}));

jest.mock('@/features/documents/services/document-files', () => ({
  deletePickerCopy: jest.fn(),
}));

async function renderForRole(role: string) {
  const store = createAppStore();
  store.dispatch(sessionAuthenticated({
    email: 'person@example.com', fullName: 'Person', plan: 'PRO', role,
    tenantId: 'tenant-1', userId: 'user-1',
  }));
  return render(
    <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, right: 0, bottom: 0, left: 0 } }}>
      <Provider store={store}>
        <DocumentUploadScreen />
      </Provider>
    </SafeAreaProvider>,
  );
}

describe('DocumentUploadScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPicker.mockResolvedValue({ canceled: true, assets: null });
  });

  it('keeps the queue unchanged when native selection is cancelled', async () => {
    const screen = await renderForRole('USER');
    await fireEvent.press(screen.getByRole('button', { name: 'Select files' }));
    await waitFor(() => expect(mockPicker).toHaveBeenCalledWith(expect.objectContaining({
      copyToCacheDirectory: true, multiple: true,
    })));
    expect(screen.getByText('No files selected yet.')).toBeTruthy();
  });

  it('never renders customer visibility for regular users but does for tenant admins', async () => {
    const regular = await renderForRole('USER');
    expect(regular.queryByRole('button', { name: 'Customers and employees' })).toBeNull();
    await regular.unmount();

    const admin = await renderForRole('TENANT_ADMIN');
    expect(admin.getByRole('button', { name: 'Customers and employees' })).toBeTruthy();
  });
});
