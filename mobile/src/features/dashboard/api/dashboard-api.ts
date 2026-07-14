import { springApi } from '@/services/api/api';
import type { DashboardSummary } from '@/features/dashboard/types';

export const dashboardApi = springApi.injectEndpoints({
  endpoints: (build) => ({
    getDashboardSummary: build.query<DashboardSummary, void>({
      query: () => '/dashboard/summary',
      providesTags: ['Dashboard'],
    }),
  }),
});

export const { useGetDashboardSummaryQuery } = dashboardApi;
