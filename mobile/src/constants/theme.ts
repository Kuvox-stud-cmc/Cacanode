import type { TextStyle, ViewStyle } from 'react-native';

const sharedColors = {
  primary: '#4F46E5',
  primaryPressed: '#4338CA',
  accent: '#7C3AED',
  success: '#16A34A',
  warning: '#D97706',
  danger: '#DC2626',
  dangerPressed: '#B91C1C',
  info: '#2563EB',
} as const;

export const themes = {
  light: {
    dark: false,
    colors: {
      ...sharedColors,
      background: '#F8FAFC',
      surface: '#FFFFFF',
      surfaceElevated: '#FFFFFF',
      primarySoft: '#EEF2FF',
      primaryText: '#4338CA',
      successText: '#15803D',
      warningText: '#B45309',
      dangerText: '#B91C1C',
      infoText: '#1D4ED8',
      text: '#0F172A',
      textMuted: '#64748B',
      border: '#E2E8F0',
      overlay: 'rgba(15, 23, 42, 0.48)',
    },
  },
  dark: {
    dark: true,
    colors: {
      ...sharedColors,
      background: '#0F172A',
      surface: '#1E293B',
      surfaceElevated: '#334155',
      primarySoft: '#312E81',
      primaryText: '#C7D2FE',
      successText: '#86EFAC',
      warningText: '#FCD34D',
      dangerText: '#FCA5A5',
      infoText: '#93C5FD',
      text: '#F8FAFC',
      textMuted: '#94A3B8',
      border: '#334155',
      overlay: 'rgba(2, 6, 23, 0.72)',
    },
  },
} as const;

export type AppTheme = (typeof themes)[keyof typeof themes];
export type AppColors = AppTheme['colors'];

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  xxxl: 48,
} as const;

export const radii = {
  sm: 8,
  md: 12,
  lg: 16,
  pill: 999,
} as const;

export const typography = {
  caption: { fontSize: 12, lineHeight: 16, fontWeight: '400' },
  bodySmall: { fontSize: 14, lineHeight: 20, fontWeight: '400' },
  body: { fontSize: 16, lineHeight: 24, fontWeight: '400' },
  heading: { fontSize: 20, lineHeight: 28, fontWeight: '600' },
  title: { fontSize: 24, lineHeight: 32, fontWeight: '700' },
  display: { fontSize: 32, lineHeight: 40, fontWeight: '700' },
} satisfies Record<string, TextStyle>;

export const elevation = {
  small: {
    elevation: 2,
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.08,
    shadowRadius: 3,
  },
  medium: {
    elevation: 6,
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.12,
    shadowRadius: 10,
  },
} satisfies Record<string, ViewStyle>;
