import {
  authenticationRequired,
  authReducer,
  sessionAuthenticated,
  twoFactorRequired,
} from '@/features/auth/store/auth-slice';
import type { AuthUser } from '@/types/auth';

const user = {
  userId: 'user-1',
  tenantId: 'tenant-1',
  email: 'person@example.com',
  fullName: 'Person Name',
  role: 'TENANT_ADMIN',
  plan: 'PRO',
} satisfies AuthUser;

describe('auth slice', () => {
  it('tracks the authentication workflow without storing credentials', () => {
    const awaiting = authReducer(undefined, twoFactorRequired(user.email));
    expect(awaiting).toMatchObject({
      status: 'awaiting_2fa',
      pendingTwoFactorEmail: user.email,
      user: null,
    });

    const authenticated = authReducer(awaiting, sessionAuthenticated(user));
    expect(authenticated).toMatchObject({ status: 'authenticated', user });
    expect(JSON.stringify(authenticated)).not.toContain('accessToken');
    expect(JSON.stringify(authenticated)).not.toContain('refreshToken');

    expect(authReducer(authenticated, authenticationRequired())).toMatchObject({
      status: 'unauthenticated',
      user: null,
    });
  });
});
