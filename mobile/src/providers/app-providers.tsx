import { DarkTheme, DefaultTheme, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { type PropsWithChildren, useEffect, useMemo } from 'react';
import { useColorScheme } from 'react-native';
import { Provider as ReduxProvider } from 'react-redux';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { I18nextProvider } from 'react-i18next';

import { AppErrorBoundary } from '@/components/feedback/app-error-boundary';
import { AuthBootstrap } from '@/features/auth/components/auth-bootstrap';
import { themes } from '@/constants/theme';
import { store } from '@/store';
import i18n, { restoreAppLanguage } from '@/i18n';

export function AppProviders({ children }: PropsWithChildren) {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const appTheme = themes[scheme];
  useEffect(() => { void restoreAppLanguage().catch(() => undefined); }, []);
  const navigationTheme = useMemo(() => {
    const base = appTheme.dark ? DarkTheme : DefaultTheme;
    return {
      ...base,
      colors: {
        ...base.colors,
        primary: appTheme.colors.primary,
        background: appTheme.colors.background,
        card: appTheme.colors.surface,
        text: appTheme.colors.text,
        border: appTheme.colors.border,
        notification: appTheme.colors.danger,
      },
    };
  }, [appTheme]);

  return (
    <I18nextProvider i18n={i18n}><SafeAreaProvider>
      <ThemeProvider value={navigationTheme}>
        <AppErrorBoundary>
          <ReduxProvider store={store}>
            <AuthBootstrap>{children}</AuthBootstrap>
          </ReduxProvider>
        </AppErrorBoundary>
        <StatusBar style={appTheme.dark ? 'light' : 'dark'} />
      </ThemeProvider>
    </SafeAreaProvider></I18nextProvider>
  );
}
