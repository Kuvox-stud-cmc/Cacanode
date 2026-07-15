import * as Linking from 'expo-linking';

import {
  billingManagementUrl,
  openBillingManagement,
} from '@/features/billing/services/billing-web-link';

jest.mock('expo-linking', () => ({ openURL: jest.fn() }));

describe('billing web link', () => {
  beforeEach(() => jest.clearAllMocks());

  it('builds the protected web billing destination', () => {
    expect(billingManagementUrl('https://app.example.com')).toBe(
      'https://app.example.com/settings?tab=quota',
    );
  });

  it('opens billing in the system browser', async () => {
    jest.mocked(Linking.openURL).mockResolvedValue(true);
    await openBillingManagement();
    expect(Linking.openURL).toHaveBeenCalledWith('http://localhost:3000/settings?tab=quota');
  });
});
