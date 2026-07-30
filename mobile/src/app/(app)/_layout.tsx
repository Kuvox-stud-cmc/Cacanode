import { Redirect, Stack } from 'expo-router';

import { AccountMenuProvider } from '@/features/navigation/components/account-menu-provider';
import { AuthenticatedHeaderRight } from '@/features/navigation/components/authenticated-header';
import { useAppTheme } from '@/hooks/use-app-theme';
import { useAppSelector } from '@/store/hooks';
import { useTranslation } from 'react-i18next';

export default function AppLayout() {
  const status = useAppSelector((state) => state.auth.status);
  if (status !== 'authenticated') return <Redirect href="/login" />;
  return (
    <AccountMenuProvider>
      <AuthenticatedStack />
    </AccountMenuProvider>
  );
}

function AuthenticatedStack() {
  const theme = useAppTheme();
  const {t}=useTranslation();
  return (
    <Stack
      screenOptions={{
        contentStyle: { backgroundColor: theme.colors.background },
        fullScreenGestureEnabled: true,
        gestureEnabled: true,
        headerBackButtonDisplayMode: 'minimal',
        headerRight: () => <AuthenticatedHeaderRight />,
        headerShadowVisible: false,
        headerStyle: { backgroundColor: theme.colors.surface },
        headerTintColor: theme.colors.text,
      }}>
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen name="conversations/index" options={{ title: t('nav.conversations') }} />
      <Stack.Screen name="conversations/[conversationId]" options={{ title: t('nav.conversation') }} />
      <Stack.Screen name="documents/upload" options={{ title: t('nav.uploadDocument') }} />
      <Stack.Screen name="documents/[documentId]" options={{ title: t('nav.document') }} />
      <Stack.Screen name="tickets/[ticketId]" options={{ title: t('nav.ticket') }} />
      <Stack.Screen name="settings/index" options={{ title: t('nav.accountSettings') }} />
      <Stack.Screen name="recruitment" options={{headerShown:false}} />
    </Stack>
  );
}
