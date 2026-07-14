import { springApi } from '@/services/api/api';
import type { TenantWorkspace } from '@/features/chat/types';

export const workspaceApi = springApi.injectEndpoints({
  endpoints: (build) => ({
    getTenantWorkspace: build.query<TenantWorkspace, void>({
      query: () => '/tenants/me/workspace',
      providesTags: ['Workspace'],
    }),
  }),
});

export const { useGetTenantWorkspaceQuery } = workspaceApi;
