import {
  canAccessPrimaryTab,
  canUseAdminCapability,
  displayRole,
  PRIMARY_TABS,
} from '@/features/navigation/role-policy';

describe('role policy', () => {
  it.each(['USER', 'TENANT_ADMIN'])('keeps every primary tab available to %s', (role) => {
    expect(PRIMARY_TABS.every((tab) => canAccessPrimaryTab(role, tab))).toBe(true);
  });

  it('limits known document administration capabilities to tenant admins', () => {
    expect(canUseAdminCapability('USER', 'changeDocumentVisibility')).toBe(false);
    expect(canUseAdminCapability('USER', 'deleteDocument')).toBe(false);
    expect(canUseAdminCapability('TENANT_ADMIN', 'changeDocumentVisibility')).toBe(true);
    expect(canUseAdminCapability('TENANT_ADMIN', 'deleteDocument')).toBe(true);
  });

  it('formats current roles for display', () => {
    expect(displayRole('TENANT_ADMIN')).toBe('Tenant admin');
    expect(displayRole('USER')).toBe('User');
  });
});
