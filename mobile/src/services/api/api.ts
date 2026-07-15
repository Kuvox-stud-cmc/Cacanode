import { createApi } from '@reduxjs/toolkit/query/react';

import { env } from '@/constants/env';
import { createApiBaseQuery } from '@/services/api/base-query';

export const springApi = createApi({
  reducerPath: 'springApi',
  baseQuery: createApiBaseQuery(env.apiBaseUrl),
  tagTypes: ['Billing', 'Dashboard', 'Document', 'Ticket', 'Workspace'],
  endpoints: () => ({}),
});

export const aiApi = createApi({
  reducerPath: 'aiApi',
  baseQuery: createApiBaseQuery(env.aiApiBaseUrl),
  tagTypes: ['Chat', 'Conversation'],
  endpoints: () => ({}),
});
