export const SUPPORTED_PHONE_COUNTRIES = [
  { region: "VN", callingCode: "84", names: { en: "Vietnam", vi: "Việt Nam" } },
  { region: "AU", callingCode: "61", names: { en: "Australia", vi: "Úc" } },
  { region: "CA", callingCode: "1", names: { en: "Canada", vi: "Canada" } },
  { region: "GB", callingCode: "44", names: { en: "United Kingdom", vi: "Vương quốc Anh" } },
  { region: "ID", callingCode: "62", names: { en: "Indonesia", vi: "Indonesia" } },
  { region: "IN", callingCode: "91", names: { en: "India", vi: "Ấn Độ" } },
  { region: "JP", callingCode: "81", names: { en: "Japan", vi: "Nhật Bản" } },
  { region: "KR", callingCode: "82", names: { en: "South Korea", vi: "Hàn Quốc" } },
  { region: "MY", callingCode: "60", names: { en: "Malaysia", vi: "Malaysia" } },
  { region: "PH", callingCode: "63", names: { en: "Philippines", vi: "Philippines" } },
  { region: "SG", callingCode: "65", names: { en: "Singapore", vi: "Singapore" } },
  { region: "TH", callingCode: "66", names: { en: "Thailand", vi: "Thái Lan" } },
  { region: "US", callingCode: "1", names: { en: "United States", vi: "Hoa Kỳ" } },
] as const;

export type SupportedPhoneCountry = (typeof SUPPORTED_PHONE_COUNTRIES)[number]["region"];

export function phoneCountry(country: SupportedPhoneCountry) {
  return SUPPORTED_PHONE_COUNTRIES.find((option) => option.region === country)!;
}

export function phoneCountryName(country: SupportedPhoneCountry, locale: string) {
  const option = phoneCountry(country);
  try {
    return new Intl.DisplayNames([locale], { type: "region" }).of(country)
      ?? option.names[locale.startsWith("vi") ? "vi" : "en"];
  } catch {
    return option.names[locale.startsWith("vi") ? "vi" : "en"];
  }
}

export function nationalPhoneDigits(value: string) {
  return value.replace(/\D/g, "").replace(/^0+/, "");
}

export function toE164(country: SupportedPhoneCountry, nationalNumber: string) {
  const digits = nationalPhoneDigits(nationalNumber);
  const value = `+${phoneCountry(country).callingCode}${digits}`;
  return digits && /^\+[1-9][0-9]{7,14}$/.test(value) ? value : null;
}
