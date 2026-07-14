import { fireEvent, render } from '@testing-library/react-native';
import { Provider } from 'react-redux';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { sessionAuthenticated } from '@/features/auth/store/auth-slice';
import { DocumentDetailScreen } from '@/features/documents/screens/document-detail-screen';
import { createAppStore } from '@/store';

const mockRefetch = jest.fn();
const mockUpdate = jest.fn();
const mockDelete = jest.fn();
const mockDownload = jest.fn();

jest.mock('expo-router', () => ({
  router: { replace: jest.fn() },
  useLocalSearchParams: () => ({ documentId: 'document-1', status: 'COMPLETED' }),
}));

jest.mock('@/hooks/use-screen-focus', () => ({ useScreenFocus: () => true }));
jest.mock('@/features/documents/services/document-files', () => ({ shareTemporaryDownload: jest.fn() }));

const mockDocument = {
  id: 'document-1', jobId: 'job-1', fileName: 'policy.pdf', fileType: 'PDF',
  fileSizeBytes: 100, knowledgeBaseId: 'kb-1', status: 'COMPLETED',
  visibility: 'EMPLOYEE_ONLY', chunkCount: 3, errorMessage: null,
  uploadedAt: '2026-07-14T08:00:00',
};

jest.mock('@/features/documents/api/documents-api', () => ({
  useGetDocumentQuery: () => ({
    data: mockDocument, isLoading: false, isFetching: false, isError: false, refetch: mockRefetch,
  }),
  useDownloadDocumentMutation: () => [mockDownload, { isLoading: false }],
  useUpdateDocumentVisibilityMutation: () => [mockUpdate, { isLoading: false }],
  useDeleteDocumentMutation: () => [mockDelete, { isLoading: false }],
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
        <DocumentDetailScreen />
      </Provider>
    </SafeAreaProvider>,
  );
}

describe('DocumentDetailScreen authorization', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUpdate.mockReturnValue({ unwrap: () => Promise.resolve(mockDocument) });
    mockDelete.mockReturnValue({ unwrap: () => Promise.resolve() });
    mockDownload.mockReturnValue({ unwrap: () => Promise.resolve({ uri: 'file:///cache/policy.pdf' }) });
  });

  it('does not render customer visibility or deletion controls for regular users', async () => {
    const screen = await renderForRole('USER');
    expect(screen.queryByRole('button', { name: 'Change visibility' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Delete document' })).toBeNull();
    expect(screen.queryByText('Customers and employees')).toBeNull();
  });

  it('renders admin controls and waits for server mutation confirmation', async () => {
    const screen = await renderForRole('TENANT_ADMIN');
    await fireEvent.press(screen.getByRole('button', { name: 'Change visibility' }));
    await fireEvent.press(screen.getByRole('button', { name: 'Customers and employees' }));
    expect(mockUpdate).toHaveBeenCalledWith({
      documentId: 'document-1', visibility: 'CUSTOMER_AND_EMPLOYEE',
    });
    expect(screen.getByRole('button', { name: 'Delete document' })).toBeTruthy();
  });
});
