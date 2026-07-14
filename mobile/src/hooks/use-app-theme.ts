import { useColorScheme } from 'react-native';

import { themes } from '@/constants/theme';

export function useAppTheme() {
  return themes[useColorScheme() === 'dark' ? 'dark' : 'light'];
}
