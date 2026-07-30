import AsyncStorage from '@react-native-async-storage/async-storage';
import { getLocales } from 'expo-localization';
import { createInstance } from 'i18next';
import { initReactI18next } from 'react-i18next';

import { en } from '@/i18n/messages/en';
import { vi } from '@/i18n/messages/vi';

export type AppLanguage = 'en' | 'vi';
export const LANGUAGE_STORAGE_KEY = 'cacanode.mobile.language';

export function languageFromLocale(locale?: string | null): AppLanguage {
  return locale?.toLowerCase().startsWith('vi') ? 'vi' : 'en';
}

export function detectDeviceLanguage(locales = getLocales()): AppLanguage {
  return languageFromLocale(locales[0]?.languageTag ?? locales[0]?.languageCode);
}

const i18n=createInstance();
void i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, vi: { translation: vi } },
  lng: detectDeviceLanguage(), fallbackLng: 'en', interpolation: { escapeValue: false },
});

export async function restoreAppLanguage(): Promise<AppLanguage> {
  const stored = await AsyncStorage.getItem(LANGUAGE_STORAGE_KEY);
  const language: AppLanguage = stored === 'vi' || stored === 'en' ? stored : detectDeviceLanguage();
  await i18n.changeLanguage(language);
  return language;
}

export async function changeAppLanguage(language: AppLanguage): Promise<void> {
  await AsyncStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  await i18n.changeLanguage(language);
}

export default i18n;
