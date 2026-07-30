export const PRIMARY_TABS = ['dashboard', 'chat', 'documents', 'tickets', 'recruitment'] as const;
export type PrimaryTab = (typeof PRIMARY_TABS)[number];

export type AdminCapability = 'changeDocumentVisibility' | 'deleteDocument';

export function canAccessPrimaryTab(role: string | undefined, tab: PrimaryTab): boolean {
  return (role === 'USER' || role === 'TENANT_ADMIN') && PRIMARY_TABS.includes(tab);
}

export function canUseAdminCapability(
  role: string | undefined,
  _capability: AdminCapability,
): boolean {
  return role === 'TENANT_ADMIN';
}

export function displayRole(role: string | undefined): string {
  if (role === 'TENANT_ADMIN') return 'Tenant admin';
  if (role === 'USER') return 'User';
  return role?.replaceAll('_', ' ').toLowerCase() ?? 'Member';
}
