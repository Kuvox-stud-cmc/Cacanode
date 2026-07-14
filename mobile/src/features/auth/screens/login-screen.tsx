import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'expo-router';
import { Controller, useForm } from 'react-hook-form';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { z } from 'zod';

import { KeyboardScreen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { TextField } from '@/components/ui/text-field';
import { useLoginMutation } from '@/features/auth/api/auth-api';
import { twoFactorRequired } from '@/features/auth/store/auth-slice';
import type { ApiError } from '@/services/api/errors';
import { commitSession } from '@/services/auth/session-manager';
import { useAppDispatch } from '@/store/hooks';
import { isLoginTwoFactorStep } from '@/types/auth';
import { spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

const schema = z.object({
  email: z.email('Enter a valid email address.'),
  password: z.string().min(8, 'Password must contain at least 8 characters.'),
});

type LoginValues = z.infer<typeof schema>;

export function LoginScreen() {
  const theme = useAppTheme();
  const dispatch = useAppDispatch();
  const router = useRouter();
  const [login, { isLoading }] = useLoginMutation();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [committing, setCommitting] = useState(false);
  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  });

  const submit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      const response = await login(values).unwrap();
      if (isLoginTwoFactorStep(response)) {
        dispatch(twoFactorRequired(response.email));
        router.replace('/verify-login-2fa');
        return;
      }

      setCommitting(true);
      await commitSession(response, dispatch);
      router.replace('/dashboard');
    } catch (error) {
      const apiError = error as Partial<ApiError>;
      setServerError(
        apiError.kind === 'network' || apiError.kind === 'timeout'
          ? apiError.message ?? 'Unable to reach the service.'
          : 'Unable to sign in with those credentials.',
      );
    } finally {
      setCommitting(false);
    }
  });

  const locked = isLoading || committing;

  return (
    <KeyboardScreen contentContainerStyle={styles.content}>
      <View style={styles.heading}>
        <AppText variant="caption" muted>CACANODE MOBILE</AppText>
        <AppText accessibilityRole="header" variant="display">Welcome back</AppText>
        <AppText muted>Sign in to continue to your workspace.</AppText>
      </View>
      <Card elevated style={styles.form}>
            <Controller
              control={control}
              name="email"
              render={({ field: { onBlur, onChange, value } }) => (
                <TextField
                  autoCapitalize="none"
                  autoComplete="email"
                  editable={!locked}
                  error={errors.email?.message}
                  keyboardType="email-address"
                  label="Email"
                  onBlur={onBlur}
                  onChangeText={onChange}
                  returnKeyType="next"
                  value={value}
                />
              )}
            />
            <View>
              <Controller
                control={control}
                name="password"
                render={({ field: { onBlur, onChange, value } }) => (
                  <TextField
                    autoComplete="password"
                    editable={!locked}
                    error={errors.password?.message}
                    label="Password"
                    onBlur={onBlur}
                    onChangeText={onChange}
                    onSubmitEditing={() => void submit()}
                    returnKeyType="done"
                    secureTextEntry={!showPassword}
                    value={value}
                    rightAccessory={
                      <Pressable
                        accessibilityLabel={showPassword ? 'Hide password' : 'Show password'}
                        accessibilityRole="button"
                        disabled={locked}
                        hitSlop={8}
                        onPress={() => setShowPassword((visible) => !visible)}
                        style={styles.passwordToggle}>
                        <AppText style={styles.toggleLabel} variant="bodySmall">
                          {showPassword ? 'Hide' : 'Show'}
                        </AppText>
                      </Pressable>
                    }
                  />
                )}
              />
            </View>
            {serverError ? (
              <AppText accessibilityRole="alert" style={[styles.inlineError, { color: theme.colors.dangerText }]}>{serverError}</AppText>
            ) : null}
            <Button loading={locked} onPress={() => void submit()}>
              Sign in
            </Button>
      </Card>
    </KeyboardScreen>
  );
}

const styles = StyleSheet.create({
  content: { flexGrow: 1, justifyContent: 'center', gap: spacing.xxl, paddingVertical: spacing.xxl },
  heading: { gap: spacing.sm },
  form: { gap: spacing.lg },
  inlineError: { textAlign: 'center' },
  passwordToggle: { alignItems: 'center', justifyContent: 'center', minHeight: 44, minWidth: 44 },
  toggleLabel: { fontWeight: '600' },
});
