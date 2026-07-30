import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'expo-router';
import { Controller, useForm } from 'react-hook-form';
import { useEffect, useMemo, useState } from 'react';
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
import { useTranslation } from 'react-i18next';
import { LanguageSelector } from '@/i18n/language-selector';
import { hasUnsupportedRole, isMobileRoleUnsupported, openPlatformAdministration, rememberUnsupportedRole } from '@/features/auth/services/mobile-role-gate';

type LoginValues = {email:string;password:string};

export function LoginScreen() {
  const theme = useAppTheme();
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const router = useRouter();
  const [login, { isLoading }] = useLoginMutation();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [committing, setCommitting] = useState(false);
  const [unsupportedRole,setUnsupportedRole]=useState(false);
  const schema=useMemo(()=>z.object({email:z.email(t('auth.invalidEmail')),password:z.string().min(8,t('auth.shortPassword'))}),[t]);
  useEffect(()=>{void hasUnsupportedRole().then(setUnsupportedRole).catch(()=>undefined)},[]);
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
      if(isMobileRoleUnsupported(apiError)){await rememberUnsupportedRole().catch(()=>undefined);setUnsupportedRole(true);return;}
      setServerError(
        apiError.kind === 'network' || apiError.kind === 'timeout'
          ? apiError.message ?? t('auth.unreachable')
          : t('auth.invalidCredentials'),
      );
    } finally {
      setCommitting(false);
    }
  });

  const locked = isLoading || committing;

  return (
    <KeyboardScreen contentContainerStyle={styles.content}>
      <View style={styles.heading}>
        <View style={styles.topRow}><AppText variant="caption" muted>{t('auth.brand')}</AppText><LanguageSelector compact /></View>
        <AppText accessibilityRole="header" variant="display">{t('auth.welcome')}</AppText>
        <AppText muted>{t('auth.signInDescription')}</AppText>
      </View>
      {unsupportedRole?<Card elevated style={styles.form}><AppText accessibilityRole="header" variant="heading">{t('auth.unsupportedTitle')}</AppText><AppText muted>{t('auth.unsupportedDescription')}</AppText><Button onPress={()=>void openPlatformAdministration()} variant="secondary">{t('auth.openPlatform')}</Button></Card>:null}
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
                  label={t('auth.email')}
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
                    label={t('auth.password')}
                    onBlur={onBlur}
                    onChangeText={onChange}
                    onSubmitEditing={() => void submit()}
                    returnKeyType="done"
                    secureTextEntry={!showPassword}
                    value={value}
                    rightAccessory={
                      <Pressable
                        accessibilityLabel={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
                        accessibilityRole="button"
                        disabled={locked}
                        hitSlop={8}
                        onPress={() => setShowPassword((visible) => !visible)}
                        style={styles.passwordToggle}>
                        <AppText style={styles.toggleLabel} variant="bodySmall">
                          {showPassword ? t('auth.hide') : t('auth.show')}
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
              {t('auth.signIn')}
            </Button>
      </Card>
    </KeyboardScreen>
  );
}

const styles = StyleSheet.create({
  content: { flexGrow: 1, justifyContent: 'center', gap: spacing.xxl, paddingVertical: spacing.xxl },
  heading: { gap: spacing.sm },
  topRow:{alignItems:'center',flexDirection:'row',justifyContent:'space-between',gap:spacing.md},
  form: { gap: spacing.lg },
  inlineError: { textAlign: 'center' },
  passwordToggle: { alignItems: 'center', justifyContent: 'center', minHeight: 44, minWidth: 44 },
  toggleLabel: { fontWeight: '600' },
});
