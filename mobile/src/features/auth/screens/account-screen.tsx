import { useRouter } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { ScrollScreen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ListRow } from '@/components/ui/list-row';
import { Separator } from '@/components/ui/separator';
import { useLogoutSessionMutation } from '@/features/auth/api/auth-api';
import { displayRole } from '@/features/navigation/role-policy';
import { clearLocalSession } from '@/services/auth/session-manager';
import { tokenVault } from '@/services/auth/token-vault';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { spacing } from '@/constants/theme';

export function AccountScreen() {
  const dispatch = useAppDispatch();
  const router = useRouter();
  const user = useAppSelector((state) => state.auth.user);
  const [logout, { isLoading }] = useLogoutSessionMutation();

  const signOut = async () => {
    const refreshToken = await tokenVault.get().catch(() => null);
    await clearLocalSession(dispatch);
    router.replace('/login');

    if (refreshToken) {
      void logout({ refreshToken }).unwrap().catch(() => undefined);
    }
  };

  return (
    <ScrollScreen edges={['right', 'bottom', 'left']} contentContainerStyle={styles.content}>
        <View style={styles.heading}>
          <AppText accessibilityRole="header" variant="title">Account settings</AppText>
          <AppText muted>Review your identity and current mobile session.</AppText>
        </View>
        <Card elevated style={styles.details}>
          <ListRow subtitle={user?.email} title={user?.fullName || 'Signed-in user'} />
          <Separator />
          <Detail label="Role" value={displayRole(user?.role)} />
          <Detail label="Plan" value={user?.plan} />
        </Card>
        <Button accessibilityLabel="Sign out" loading={isLoading} onPress={() => void signOut()} variant="danger">
          Sign out
        </Button>
    </ScrollScreen>
  );
}

function Detail({ label, value }: { label: string; value?: string }) {
  return (
    <View style={styles.detailRow}>
      <AppText muted variant="bodySmall">{label}</AppText>
      <Badge tone="primary">{value ?? '—'}</Badge>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.xl, paddingVertical: spacing.xxl },
  heading: { gap: spacing.sm },
  details: { gap: spacing.lg },
  detailRow: { gap: spacing.xs },
});
