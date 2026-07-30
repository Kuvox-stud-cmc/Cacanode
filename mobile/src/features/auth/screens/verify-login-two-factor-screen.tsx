import { zodResolver } from '@hookform/resolvers/zod';
import { Redirect, useRouter } from 'expo-router';
import { Controller, useForm } from 'react-hook-form';
import { useEffect, useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { z } from 'zod';

import { KeyboardScreen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { TextField } from '@/components/ui/text-field';
import {
  useResendLoginTwoFactorMutation,
  useVerifyLoginTwoFactorMutation,
} from '@/features/auth/api/auth-api';
import type { ApiError } from '@/services/api/errors';
import { commitSession } from '@/services/auth/session-manager';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';
import { useTranslation } from 'react-i18next';
import { LanguageSelector } from '@/i18n/language-selector';
import { isMobileRoleUnsupported, openPlatformAdministration, rememberUnsupportedRole } from '@/features/auth/services/mobile-role-gate';

type CodeValues = {code:string};

export function VerifyLoginTwoFactorScreen() {
  const theme = useAppTheme();
  const { t }=useTranslation();
  const dispatch = useAppDispatch();
  const router = useRouter();
  const pendingEmail = useAppSelector((state) => state.auth.pendingTwoFactorEmail);
  const [verify, { isLoading: isVerifying }] = useVerifyLoginTwoFactorMutation();
  const [resend, { isLoading: isResending }] = useResendLoginTwoFactorMutation();
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);
  const [unsupportedRole,setUnsupportedRole]=useState(false);
  const schema=useMemo(()=>z.object({code:z.string().regex(/^\d{6}$/,t('auth.invalidCodeFormat'))}),[t]);
  const { control, handleSubmit, formState: { errors } } = useForm<CodeValues>({
    resolver: zodResolver(schema),
    defaultValues: { code: '' },
  });

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => setCooldown((value) => Math.max(0, value - 1)), 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  if (!pendingEmail) return <Redirect href="/login" />;

  const locked = isVerifying || isResending;

  const submit = handleSubmit(async ({ code }) => {
    setError(null);
    setNotice(null);
    try {
      const credentials = await verify({ email: pendingEmail, code }).unwrap();
      await commitSession(credentials, dispatch);
      router.replace('/dashboard');
    } catch (failure) {
      const apiError = failure as Partial<ApiError>;
      if(isMobileRoleUnsupported(apiError)){await rememberUnsupportedRole().catch(()=>undefined);setUnsupportedRole(true);return;}
      setError(
        apiError.kind === 'network' || apiError.kind === 'timeout'
          ? apiError.message ?? t('auth.unreachable')
          : t('auth.invalidCode'),
      );
    }
  });

  const requestAnotherCode = async () => {
    setError(null);
    setNotice(null);
    try {
      const response = await resend({ email: pendingEmail }).unwrap();
      const seconds = response.canRetryAfterSeconds ?? 60;
      setCooldown(seconds);
      setNotice(response.message);
    } catch (failure) {
      const apiError = failure as Partial<ApiError>;
      setError(apiError.message ?? t('auth.resendFailed'));
    }
  };

  return (
    <KeyboardScreen contentContainerStyle={styles.content}>
      <View style={styles.heading}>
        <View style={styles.topRow}><AppText accessibilityRole="header" variant="title">{t('auth.checkEmail')}</AppText><LanguageSelector compact /></View>
        <AppText muted>{t('auth.codeDescription',{email:pendingEmail})}</AppText>
      </View>
      {unsupportedRole?<Card elevated style={styles.form}><AppText accessibilityRole="header" variant="heading">{t('auth.unsupportedTitle')}</AppText><AppText muted>{t('auth.unsupportedDescription')}</AppText><Button onPress={()=>void openPlatformAdministration()} variant="secondary">{t('auth.openPlatform')}</Button></Card>:null}
      <Card elevated style={styles.form}>
            <Controller
              control={control}
              name="code"
              render={({ field: { onBlur, onChange, value } }) => (
                <TextField
                  autoComplete="one-time-code"
                  editable={!locked}
                  error={errors.code?.message}
                  keyboardType="number-pad"
                  label={t('auth.code')}
                  maxLength={6}
                  onBlur={onBlur}
                  onChangeText={(text) => onChange(text.replace(/\D/g, '').slice(0, 6))}
                  onSubmitEditing={() => void submit()}
                  returnKeyType="done"
                  value={value}
                />
              )}
            />
            {error ? <AppText accessibilityRole="alert" style={[styles.error, { color: theme.colors.dangerText }]}>{error}</AppText> : null}
            {notice ? <AppText style={styles.notice}>{notice}</AppText> : null}
            <Button disabled={isResending} loading={isVerifying} onPress={() => void submit()}>
              {t('auth.verify')}
            </Button>
            <Button
              accessibilityLabel={t('auth.resendLabel')}
              disabled={cooldown > 0 || isVerifying}
              loading={isResending}
              onPress={() => void requestAnotherCode()}
              variant="secondary">
              {cooldown > 0 ? t('auth.resendIn',{seconds:cooldown}) : t('auth.resend')}
            </Button>
      </Card>
    </KeyboardScreen>
  );
}

const styles = StyleSheet.create({
  content: { flexGrow: 1, justifyContent: 'center', gap: spacing.xxl, paddingVertical: spacing.xxl },
  error: { textAlign: 'center' },
  heading: { gap: spacing.sm },
  topRow:{alignItems:'center',flexDirection:'row',justifyContent:'space-between',gap:spacing.md},
  form: { gap: spacing.lg },
  notice: { textAlign: 'center' },
});
