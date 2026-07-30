import { languageFromLocale } from '@/i18n';
import { en } from '@/i18n/messages/en';
import { vi } from '@/i18n/messages/vi';

function messageKeys(value: object, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof child === 'string' ? [path] : messageKeys(child, path);
  });
}

describe('mobile language selection',()=>{
  it('selects Vietnamese for Vietnamese device locales',()=>{
    expect(languageFromLocale('vi-VN')).toBe('vi');
    expect(languageFromLocale('VI')).toBe('vi');
  });
  it('falls back to English for every other or missing locale',()=>{
    expect(languageFromLocale('en-US')).toBe('en');
    expect(languageFromLocale('fr-FR')).toBe('en');
    expect(languageFromLocale(undefined)).toBe('en');
  });
  it('keeps the English and Vietnamese catalogs in exact key parity',()=>{
    expect(messageKeys(vi).sort()).toEqual(messageKeys(en).sort());
  });
});
