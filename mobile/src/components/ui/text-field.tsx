import type { ReactNode } from 'react';
import { StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { radii, spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

type TextFieldProps = TextInputProps & {
  error?: string;
  label: string;
  rightAccessory?: ReactNode;
};

export function TextField({ error, label, rightAccessory, style, ...props }: TextFieldProps) {
  const theme = useAppTheme();
  const errorId = `${label.toLowerCase().replace(/\s+/g, '-')}-error`;

  return (
    <View style={styles.field}>
      <AppText style={styles.label} variant="bodySmall">
        {label}
      </AppText>
      <View style={styles.inputRow}>
        <TextInput
          accessibilityLabel={label}
          accessibilityState={{ disabled: props.editable === false }}
          accessibilityValue={error ? { text: `Error: ${error}` } : undefined}
          placeholderTextColor={theme.colors.textMuted}
          style={[
            styles.input,
            {
              backgroundColor: theme.colors.surface,
              borderColor: error ? theme.colors.dangerText : theme.colors.border,
              color: theme.colors.text,
            },
            rightAccessory ? styles.withAccessory : undefined,
            style,
          ]}
          {...props}
        />
        {rightAccessory ? <View style={styles.accessory}>{rightAccessory}</View> : null}
      </View>
      {error ? (
        <AppText
          accessibilityRole="alert"
          nativeID={errorId}
          style={{ color: theme.colors.dangerText }}
          variant="caption">
          {error}
        </AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  accessory: {
    alignItems: 'center',
    bottom: 0,
    justifyContent: 'center',
    minHeight: 48,
    minWidth: 48,
    position: 'absolute',
    right: 2,
    top: 0,
  },
  field: { gap: spacing.sm },
  input: {
    borderRadius: radii.md,
    borderWidth: 1,
    fontSize: 16,
    minHeight: 52,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  inputRow: { position: 'relative' },
  label: { fontWeight: '600' },
  withAccessory: { paddingRight: 56 },
});
