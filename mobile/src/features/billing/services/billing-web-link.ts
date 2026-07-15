import * as Linking from 'expo-linking';

import { env } from '@/constants/env';

export function billingManagementUrl(baseUrl = env.webAppUrl): string {
  return new URL('settings?tab=quota', `${baseUrl}/`).toString();
}

export async function openBillingManagement(): Promise<void> {
  await Linking.openURL(billingManagementUrl());
}
