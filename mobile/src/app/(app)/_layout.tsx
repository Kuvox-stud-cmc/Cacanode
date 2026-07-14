import { Redirect, Stack } from 'expo-router';

import { AccountMenuProvider } from '@/features/navigation/components/account-menu-provider';
import { AuthenticatedHeaderRight } from '@/features/navigation/components/authenticated-header';
import { useAppTheme } from '@/hooks/use-app-theme';
import { useAppSelector } from '@/store/hooks';

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
      <Stack.Screen name="conversations/index" options={{ title: 'Conversations' }} />
      <Stack.Screen name="conversations/[conversationId]" options={{ title: 'Conversation' }} />
      <Stack.Screen name="documents/upload" options={{ title: 'Upload document' }} />
      <Stack.Screen name="documents/[documentId]" options={{ title: 'Document' }} />
      <Stack.Screen name="tickets/[ticketId]" options={{ title: 'Ticket' }} />
      <Stack.Screen name="settings/index" options={{ title: 'Account settings' }} />
    </Stack>
  );
}
