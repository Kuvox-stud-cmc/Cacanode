import { springApi } from '@/services/api/api';

import type { BillingAccount } from '@/features/billing/types';

export const billingApi = springApi.injectEndpoints({
  endpoints: (build) => ({
    getBillingAccount: build.query<BillingAccount, void>({
      query: () => '/billing/account',
      providesTags: ['Billing'],
    }),
  }),
});

export const { useGetBillingAccountQuery, useLazyGetBillingAccountQuery } = billingApi;
