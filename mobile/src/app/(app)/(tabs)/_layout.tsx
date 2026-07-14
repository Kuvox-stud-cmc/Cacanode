import { Tabs } from 'expo-router';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import type { ColorValue } from 'react-native';

import { AuthenticatedHeaderRight } from '@/features/navigation/components/authenticated-header';
import { canAccessPrimaryTab } from '@/features/navigation/role-policy';
import { useAppTheme } from '@/hooks/use-app-theme';
import { useAppSelector } from '@/store/hooks';

type TabIconProps = {
  color: ColorValue;
  size: number;
};

function createTabIcon(name: SymbolViewProps['name']) {
  return function TabIcon({ color, size }: TabIconProps) {
    return <SymbolView name={name} size={size} tintColor={color} />;
  };
}

export default function AppTabsLayout() {
  const theme = useAppTheme();
  const role = useAppSelector((state) => state.auth.user?.role);

  return (
    <Tabs
      initialRouteName="dashboard"
      screenOptions={{
        headerRight: () => <AuthenticatedHeaderRight />,
        headerShadowVisible: false,
        headerStyle: { backgroundColor: theme.colors.surface },
        headerTintColor: theme.colors.text,
        tabBarActiveTintColor: theme.colors.primary,
        tabBarInactiveTintColor: theme.colors.textMuted,
        tabBarStyle: {
          backgroundColor: theme.colors.surface,
          borderTopColor: theme.colors.border,
        },
      }}>
      <Tabs.Screen
        name="dashboard"
        options={{
          href: canAccessPrimaryTab(role, 'dashboard') ? undefined : null,
          title: 'Dashboard',
          tabBarIcon: createTabIcon({ ios: 'chart.bar.fill', android: 'dashboard' }),
        }}
      />
      <Tabs.Screen
        name="chat"
        options={{
          href: canAccessPrimaryTab(role, 'chat') ? undefined : null,
          title: 'Chat',
          tabBarIcon: createTabIcon({ ios: 'bubble.left.and.bubble.right.fill', android: 'chat' }),
        }}
      />
      <Tabs.Screen
        name="documents"
        options={{
          href: canAccessPrimaryTab(role, 'documents') ? undefined : null,
          title: 'Documents',
          tabBarIcon: createTabIcon({ ios: 'doc.text.fill', android: 'description' }),
        }}
      />
      <Tabs.Screen
        name="tickets"
        options={{
          href: canAccessPrimaryTab(role, 'tickets') ? undefined : null,
          title: 'Tickets',
          tabBarIcon: createTabIcon({ ios: 'ticket.fill', android: 'confirmation_number' }),
        }}
      />
    </Tabs>
  );
}
