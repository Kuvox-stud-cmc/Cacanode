import { useRouter } from 'expo-router';
import { createContext, type PropsWithChildren, useContext, useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ListRow } from '@/components/ui/list-row';
import { Separator } from '@/components/ui/separator';
import { Sheet } from '@/components/ui/sheet';
import { spacing } from '@/constants/theme';
import { useLogoutSessionMutation } from '@/features/auth/api/auth-api';
import { displayRole } from '@/features/navigation/role-policy';
import { clearLocalSession } from '@/services/auth/session-manager';
import { tokenVault } from '@/services/auth/token-vault';
import { useAppDispatch, useAppSelector } from '@/store/hooks';

type AccountMenuContextValue = { close: () => void; open: () => void };
const AccountMenuContext = createContext<AccountMenuContextValue | null>(null);

export function AccountMenuProvider({ children }: PropsWithChildren) {
  const [visible, setVisible] = useState(false);
  const value = useMemo(
    () => ({ close: () => setVisible(false), open: () => setVisible(true) }),
    [],
  );

  return (
    <AccountMenuContext.Provider value={value}>
      {children}
      <AccountMenuSheet onDismiss={value.close} visible={visible} />
    </AccountMenuContext.Provider>
  );
}

export function useAccountMenu() {
  const value = useContext(AccountMenuContext);
  if (!value) throw new Error('useAccountMenu must be used within AccountMenuProvider.');
  return value;
}

function AccountMenuSheet({ onDismiss, visible }: { onDismiss: () => void; visible: boolean }) {
  const dispatch = useAppDispatch();
  const router = useRouter();
  const user = useAppSelector((state) => state.auth.user);
  const [logout, { isLoading }] = useLogoutSessionMutation();

  const openSettings = () => {
    onDismiss();
    router.push('/settings');
  };

  const signOut = async () => {
    const refreshToken = await tokenVault.get().catch(() => null);
    await clearLocalSession(dispatch);
    onDismiss();
    router.replace('/login');
    if (refreshToken) void logout({ refreshToken }).unwrap().catch(() => undefined);
  };

  return (
    <Sheet onDismiss={onDismiss} title="Account" visible={visible}>
      <View style={styles.content}>
        <ListRow
          subtitle={user?.email}
          title={user?.fullName || user?.email || 'Signed-in user'}
          trailing={<Badge tone="primary">{displayRole(user?.role)}</Badge>}
        />
        <View style={styles.planRow}>
          <Badge>{user?.plan ?? 'Current plan'}</Badge>
        </View>
        <Separator />
        <ListRow
          accessibilityHint="Opens account settings"
          onPress={openSettings}
          subtitle="Identity, role, plan, and session"
          title="Account settings"
          trailing={<Badge tone="neutral">Open</Badge>}
        />
        <Button
          accessibilityLabel="Sign out"
          loading={isLoading}
          onPress={() => void signOut()}
          variant="danger">
          Sign out
        </Button>
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.lg },
  planRow: { paddingHorizontal: spacing.md },
});
