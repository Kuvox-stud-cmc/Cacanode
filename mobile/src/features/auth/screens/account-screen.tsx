import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';

import { ScrollScreen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ListRow } from '@/components/ui/list-row';
import { Separator } from '@/components/ui/separator';
import { useLogoutSessionMutation } from '@/features/auth/api/auth-api';
import { useGetBillingAccountQuery } from '@/features/billing/api/billing-api';
import { PlanStatusBadge } from '@/features/billing/components/plan-status-badge';
import { formatBillingDate } from '@/features/billing/model/billing-presentation';
import { openBillingManagement } from '@/features/billing/services/billing-web-link';
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
  const { data: billingAccount } = useGetBillingAccountQuery();
  const [openingBilling, setOpeningBilling] = useState(false);

  const signOut = async () => {
    const refreshToken = await tokenVault.get().catch(() => null);
    await clearLocalSession(dispatch);
    router.replace('/login');

    if (refreshToken) {
      void logout({ refreshToken }).unwrap().catch(() => undefined);
    }
  };

  const openBilling = async () => {
    setOpeningBilling(true);
    try {
      await openBillingManagement();
    } catch {
      Alert.alert('Unable to open billing', 'Open the CacaNode web app and go to Settings → Quota Management.');
    } finally {
      setOpeningBilling(false);
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
          <View style={styles.detailRow}>
            <AppText muted variant="bodySmall">Plan</AppText>
            <PlanStatusBadge account={billingAccount} fallbackPlan={user?.plan} />
          </View>
          {billingAccount?.trialEndsAt ? (
            <Detail label="Trial ends" value={formatBillingDate(billingAccount.trialEndsAt)} />
          ) : null}
          {billingAccount?.paidThroughAt ? (
            <Detail label="Paid through" value={formatBillingDate(billingAccount.paidThroughAt)} />
          ) : null}
          {billingAccount?.graceEndsAt && billingAccount.status === 'GRACE' ? (
            <Detail label="Grace ends" value={formatBillingDate(billingAccount.graceEndsAt)} tone="danger" />
          ) : null}
        </Card>
        {billingAccount ? (
          <Card elevated style={styles.details}>
            <AppText accessibilityRole="header" variant="heading">Hiring usage</AppText>
            <UsageDetail label="Active jobs" used={billingAccount.activeJobs.used} reserved={billingAccount.activeJobs.reserved} limit={billingAccount.activeJobs.limit} />
            <UsageDetail label="Verified applications" used={billingAccount.verifiedApplications.used} reserved={billingAccount.verifiedApplications.reserved} limit={billingAccount.verifiedApplications.limit} />
            <UsageDetail label="Interview minutes" used={billingAccount.interviewSeconds.used / 60} reserved={billingAccount.interviewSeconds.reserved / 60} limit={billingAccount.interviewSeconds.limit / 60} suffix=" min" />
            <UsageDetail label="CV analyses" used={billingAccount.cvAnalyses.used} reserved={billingAccount.cvAnalyses.reserved} limit={billingAccount.cvAnalyses.limit} />
            <UsageDetail label="Recruitment storage" used={billingAccount.recruitmentStorageBytes.used / 1024 / 1024} reserved={billingAccount.recruitmentStorageBytes.reserved / 1024 / 1024} limit={billingAccount.recruitmentStorageBytes.limit / 1024 / 1024} suffix=" MB" />
          </Card>
        ) : null}
        {user?.role === 'TENANT_ADMIN' ? (
          <Button
            accessibilityLabel="Manage billing on web"
            loading={openingBilling}
            onPress={() => void openBilling()}
            variant="secondary">
            Manage billing on web
          </Button>
        ) : null}
        <Button accessibilityLabel="Sign out" loading={isLoading} onPress={() => void signOut()} variant="danger">
          Sign out
        </Button>
    </ScrollScreen>
  );
}

function UsageDetail({
  label,
  limit,
  reserved,
  suffix = '',
  used,
}: {
  label: string;
  limit: number;
  reserved: number;
  suffix?: string;
  used: number;
}) {
  const value = `${used.toLocaleString()}${suffix} / ${limit.toLocaleString()}${suffix}`;
  return (
    <View style={styles.detailRow}>
      <AppText muted variant="bodySmall">{label}</AppText>
      <AppText>{value}</AppText>
      {reserved > 0 ? <AppText muted variant="bodySmall">{reserved.toLocaleString()}{suffix} reserved</AppText> : null}
    </View>
  );
}

function Detail({
  label,
  tone = 'primary',
  value,
}: {
  label: string;
  tone?: 'primary' | 'danger';
  value?: string;
}) {
  return (
    <View style={styles.detailRow}>
      <AppText muted variant="bodySmall">{label}</AppText>
      <Badge tone={tone}>{value ?? '—'}</Badge>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.xl, paddingVertical: spacing.xxl },
  heading: { gap: spacing.sm },
  details: { gap: spacing.lg },
  detailRow: { gap: spacing.xs },
});
