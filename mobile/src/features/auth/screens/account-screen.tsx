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
import { useTranslation } from 'react-i18next';
import { LanguageSelector } from '@/i18n/language-selector';

export function AccountScreen() {
  const {t,i18n}=useTranslation();
  const locale=i18n.resolvedLanguage?.startsWith('vi')?'vi-VN':'en-US';
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
      Alert.alert(t('account.billingErrorTitle'),t('account.billingErrorDescription'));
    } finally {
      setOpeningBilling(false);
    }
  };

  return (
    <ScrollScreen edges={['right', 'bottom', 'left']} contentContainerStyle={styles.content}>
        <View style={styles.heading}>
          <AppText accessibilityRole="header" variant="title">{t('account.title')}</AppText>
          <AppText muted>{t('account.description')}</AppText>
        </View>
        <Card elevated style={styles.details}>
          <ListRow subtitle={user?.email} title={user?.fullName || t('account.signedInUser')} />
          <Separator />
          <Detail label={t('account.role')} value={user?.role==='TENANT_ADMIN'?t('account.tenantAdmin'):user?.role==='USER'?t('account.user'):displayRole(user?.role)} />
          <View style={styles.detailRow}>
            <AppText muted variant="bodySmall">{t('account.plan')}</AppText>
            <PlanStatusBadge account={billingAccount} fallbackPlan={user?.plan} />
          </View>
          {billingAccount?.trialEndsAt ? (
            <Detail label={t('account.trialEnds')} value={formatBillingDate(billingAccount.trialEndsAt,locale)} />
          ) : null}
          {billingAccount?.paidThroughAt ? (
            <Detail label={t('account.paidThrough')} value={formatBillingDate(billingAccount.paidThroughAt,locale)} />
          ) : null}
          {billingAccount?.graceEndsAt && billingAccount.status === 'GRACE' ? (
            <Detail label={t('account.graceEnds')} value={formatBillingDate(billingAccount.graceEndsAt,locale)} tone="danger" />
          ) : null}
        </Card>
        <Card elevated><LanguageSelector /></Card>
        {billingAccount ? (
          <Card elevated style={styles.details}>
            <AppText accessibilityRole="header" variant="heading">{t('account.hiringUsage')}</AppText>
            <UsageDetail label={t('account.activeJobs')} used={billingAccount.activeJobs.used} reserved={billingAccount.activeJobs.reserved} limit={billingAccount.activeJobs.limit} />
            <UsageDetail label={t('account.verifiedApplications')} used={billingAccount.verifiedApplications.used} reserved={billingAccount.verifiedApplications.reserved} limit={billingAccount.verifiedApplications.limit} />
            <UsageDetail label={t('account.interviewMinutes')} used={billingAccount.interviewSeconds.used / 60} reserved={billingAccount.interviewSeconds.reserved / 60} limit={billingAccount.interviewSeconds.limit / 60} suffix=" min" />
            <UsageDetail label={t('account.cvAnalyses')} used={billingAccount.cvAnalyses.used} reserved={billingAccount.cvAnalyses.reserved} limit={billingAccount.cvAnalyses.limit} />
            <UsageDetail label={t('account.recruitmentStorage')} used={billingAccount.recruitmentStorageBytes.used / 1024 / 1024} reserved={billingAccount.recruitmentStorageBytes.reserved / 1024 / 1024} limit={billingAccount.recruitmentStorageBytes.limit / 1024 / 1024} suffix=" MB" />
          </Card>
        ) : null}
        {user?.role === 'TENANT_ADMIN' ? (
          <Button
            accessibilityLabel={t('account.manageBilling')}
            loading={openingBilling}
            onPress={() => void openBilling()}
            variant="secondary">
            {t('account.manageBilling')}
          </Button>
        ) : null}
        <Button accessibilityLabel={t('account.signOut')} loading={isLoading} onPress={() => void signOut()} variant="danger">
          {t('account.signOut')}
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
  const {t}=useTranslation();
  const value = `${used.toLocaleString()}${suffix} / ${limit.toLocaleString()}${suffix}`;
  return (
    <View style={styles.detailRow}>
      <AppText muted variant="bodySmall">{label}</AppText>
      <AppText>{value}</AppText>
      {reserved > 0 ? <AppText muted variant="bodySmall">{t('account.reserved',{value:`${reserved.toLocaleString()}${suffix}`})}</AppText> : null}
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
