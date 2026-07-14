import { Image, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Card } from '@/components/ui/card';
import { radii, spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

type FoundationPlaceholderProps = {
  description: string;
  eyebrow: string;
  showLogo?: boolean;
  title: string;
};

export function FoundationPlaceholder({
  description,
  eyebrow,
  showLogo = false,
  title,
}: FoundationPlaceholderProps) {
  const theme = useAppTheme();

  return (
    <Screen edges={['right', 'bottom', 'left']} style={styles.screen}>
      <Card elevated style={styles.card}>
        {showLogo ? (
          <Image
            accessibilityLabel="CacaNode"
            source={require('@/assets/images/icon.png')}
            style={styles.logo}
          />
        ) : (
          <View style={[styles.marker, { backgroundColor: theme.colors.primarySoft }]} />
        )}
        <AppText
          style={[styles.eyebrow, { color: theme.colors.primaryText }]}
          variant="bodySmall">
          {eyebrow}
        </AppText>
        <AppText accessibilityRole="header" style={styles.centered} variant="title">
          {title}
        </AppText>
        <AppText muted style={styles.centered} variant="body">
          {description}
        </AppText>
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: {
    alignItems: 'center',
    gap: spacing.md,
    maxWidth: 480,
    width: '100%',
  },
  centered: {
    textAlign: 'center',
  },
  eyebrow: {
    fontWeight: '600',
    textTransform: 'uppercase',
  },
  logo: {
    borderRadius: radii.lg,
    height: 72,
    marginBottom: spacing.sm,
    width: 72,
  },
  marker: {
    borderRadius: radii.pill,
    height: 12,
    marginBottom: spacing.sm,
    width: 48,
  },
  screen: {
    alignItems: 'center',
    justifyContent: 'center',
  },
});
