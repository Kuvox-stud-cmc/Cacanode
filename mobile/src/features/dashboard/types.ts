export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export type RecentDocument = {
  id: string;
  fileName: string;
  fileType: string;
  status: DocumentStatus;
  fileSizeBytes: number;
  uploadedAt: string;
};

export type DashboardSummary = {
  totalDocuments: number;
  documentsAddedThisWeek: number;
  userMessagesThisMonth: number;
  userMessagesPreviousMonth: number;
  storedDocumentBytes: number;
  storageLimitBytes: number;
  activeUsers: number;
  activeUsersAddedThisWeek: number;
  recentDocuments: RecentDocument[];
};
