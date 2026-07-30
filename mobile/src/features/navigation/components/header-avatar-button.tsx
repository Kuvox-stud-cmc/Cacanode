import { Pressable, StyleSheet } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { radii } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';
import { useTranslation } from 'react-i18next';

export function getAvatarInitials(fullName?: string, email?: string): string {
  const names = fullName?.trim().split(/\s+/).filter(Boolean) ?? [];
  if (names.length >= 2) return `${names[0][0]}${names[names.length - 1][0]}`.toUpperCase();
  if (names.length === 1) return names[0].slice(0, 2).toUpperCase();
  return email?.slice(0, 2).toUpperCase() || 'ME';
}

export function HeaderAvatarButton({
  email,
  fullName,
  onPress,
}: {
  email?: string;
  fullName?: string;
  onPress: () => void;
}) {
  const theme = useAppTheme();
  const {t}=useTranslation();
  return (
    <Pressable
      accessibilityHint={t('accessibility.accountMenuHint')}
      accessibilityLabel={t('accessibility.openAccountMenu')}
      accessibilityRole="button"
      hitSlop={8}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: theme.colors.primary, opacity: pressed ? 0.72 : 1 },
      ]}>
      <AppText style={styles.initials} variant="bodySmall">
        {getAvatarInitials(fullName, email)}
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    alignItems: 'center',
    borderRadius: radii.pill,
    height: 40,
    justifyContent: 'center',
    width: 40,
  },
  initials: { color: '#FFFFFF', fontWeight: '800' },
});
