import { zodResolver } from '@hookform/resolvers/zod';
import { Redirect, useRouter } from 'expo-router';
import { Controller, useForm } from 'react-hook-form';
import { useEffect, useState } from 'react';
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

const schema = z.object({ code: z.string().regex(/^\d{6}$/, 'Enter the six-digit code.') });
type CodeValues = z.infer<typeof schema>;

export function VerifyLoginTwoFactorScreen() {
  const theme = useAppTheme();
  const dispatch = useAppDispatch();
  const router = useRouter();
  const pendingEmail = useAppSelector((state) => state.auth.pendingTwoFactorEmail);
  const [verify, { isLoading: isVerifying }] = useVerifyLoginTwoFactorMutation();
  const [resend, { isLoading: isResending }] = useResendLoginTwoFactorMutation();
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);
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
      setError(
        apiError.kind === 'network' || apiError.kind === 'timeout'
          ? apiError.message ?? 'Unable to reach the service.'
          : 'That code is invalid or expired. Request a new code and try again.',
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
      setError(apiError.message ?? 'Unable to send a new code. Please try again.');
    }
  };

  return (
    <KeyboardScreen contentContainerStyle={styles.content}>
      <View style={styles.heading}>
        <AppText accessibilityRole="header" variant="title">Check your email</AppText>
        <AppText muted>Enter the six-digit confirmation code sent to {pendingEmail}.</AppText>
      </View>
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
                  label="Confirmation code"
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
              Verify and continue
            </Button>
            <Button
              accessibilityLabel="Resend confirmation code"
              disabled={cooldown > 0 || isVerifying}
              loading={isResending}
              onPress={() => void requestAnotherCode()}
              variant="secondary">
              {cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend code'}
            </Button>
      </Card>
    </KeyboardScreen>
  );
}

const styles = StyleSheet.create({
  content: { flexGrow: 1, justifyContent: 'center', gap: spacing.xxl, paddingVertical: spacing.xxl },
  error: { textAlign: 'center' },
  heading: { gap: spacing.sm },
  form: { gap: spacing.lg },
  notice: { textAlign: 'center' },
});
