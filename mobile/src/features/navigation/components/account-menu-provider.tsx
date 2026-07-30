import { useRouter } from 'expo-router';
import { createContext, type PropsWithChildren, useContext, useEffect, useMemo, useState } from 'react';
import { Alert, AppState, StyleSheet, View } from 'react-native';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ListRow } from '@/components/ui/list-row';
import { Separator } from '@/components/ui/separator';
import { Sheet } from '@/components/ui/sheet';
import { spacing } from '@/constants/theme';
import { useLogoutSessionMutation } from '@/features/auth/api/auth-api';
import { useLazyGetBillingAccountQuery } from '@/features/billing/api/billing-api';
import { PlanStatusBadge } from '@/features/billing/components/plan-status-badge';
import { openBillingManagement } from '@/features/billing/services/billing-web-link';
import type { BillingAccount } from '@/features/billing/types';
import { displayRole } from '@/features/navigation/role-policy';
import { clearLocalSession } from '@/services/auth/session-manager';
import { tokenVault } from '@/services/auth/token-vault';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { useTranslation } from 'react-i18next';

type AccountMenuContextValue = { close: () => void; open: () => void };
const AccountMenuContext = createContext<AccountMenuContextValue | null>(null);

export function AccountMenuProvider({ children }: PropsWithChildren) {
  const [visible, setVisible] = useState(false);
  const [loadBillingAccount, { data: billingAccount }] = useLazyGetBillingAccountQuery();
  const value = useMemo(
    () => ({
      close: () => setVisible(false),
      open: () => {
        setVisible(true);
        void loadBillingAccount();
      },
    }),
    [loadBillingAccount],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') void loadBillingAccount();
    });
    return () => subscription.remove();
  }, [loadBillingAccount]);

  return (
    <AccountMenuContext.Provider value={value}>
      {children}
      <AccountMenuSheet
        billingAccount={billingAccount}
        onDismiss={value.close}
        visible={visible}
      />
    </AccountMenuContext.Provider>
  );
}

export function useAccountMenu() {
  const value = useContext(AccountMenuContext);
  if (!value) throw new Error('useAccountMenu must be used within AccountMenuProvider.');
  return value;
}

function AccountMenuSheet({
  billingAccount,
  onDismiss,
  visible,
}: {
  billingAccount?: BillingAccount;
  onDismiss: () => void;
  visible: boolean;
}) {
  const {t}=useTranslation();
  const dispatch = useAppDispatch();
  const router = useRouter();
  const user = useAppSelector((state) => state.auth.user);
  const [logout, { isLoading }] = useLogoutSessionMutation();

  const openSettings = () => {
    onDismiss();
    router.push('/settings');
  };

  const openBilling = async () => {
    try {
      await openBillingManagement();
      onDismiss();
    } catch {
      Alert.alert(t('account.billingErrorTitle'),t('account.billingErrorDescription'));
    }
  };

  const signOut = async () => {
    const refreshToken = await tokenVault.get().catch(() => null);
    await clearLocalSession(dispatch);
    onDismiss();
    router.replace('/login');
    if (refreshToken) void logout({ refreshToken }).unwrap().catch(() => undefined);
  };

  return (
    <Sheet onDismiss={onDismiss} title={t('account.menuTitle')} visible={visible}>
      <View style={styles.content}>
        <ListRow
          subtitle={user?.email}
          title={user?.fullName || user?.email || t('account.signedInUser')}
          trailing={<Badge tone="primary">{user?.role==='TENANT_ADMIN'?t('account.tenantAdmin'):user?.role==='USER'?t('account.user'):displayRole(user?.role)}</Badge>}
        />
        <View style={styles.planRow}>
          <PlanStatusBadge account={billingAccount} fallbackPlan={user?.plan} />
        </View>
        <Separator />
        <ListRow
          accessibilityHint={t('dashboard.openHint',{title:t('account.title')})}
          onPress={openSettings}
          subtitle={t('account.menuDescription')}
          title={t('account.title')}
          trailing={<Badge tone="neutral">{t('common.open')}</Badge>}
        />
        {user?.role === 'TENANT_ADMIN' ? (
          <ListRow
            accessibilityHint={t('account.billingDescription')}
            onPress={() => void openBilling()}
            subtitle={t('account.billingDescription')}
            title={t('account.manageBilling')}
            trailing={<Badge tone="primary">{t('common.web')}</Badge>}
          />
        ) : null}
        <Button
          accessibilityLabel={t('account.signOut')}
          loading={isLoading}
          onPress={() => void signOut()}
          variant="danger">
          {t('account.signOut')}
        </Button>
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.lg },
  planRow: { paddingHorizontal: spacing.md },
});
