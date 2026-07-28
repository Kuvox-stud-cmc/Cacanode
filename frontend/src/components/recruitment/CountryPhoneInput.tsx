"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  phoneCountry,
  phoneCountryName,
  SUPPORTED_PHONE_COUNTRIES,
  type SupportedPhoneCountry,
} from "@/lib/recruitment-phone";

type CountryPhoneInputProps = {
  country: SupportedPhoneCountry;
  nationalNumber: string;
  locale: string;
  countryLabel: string;
  numberLabel: string;
  help: string;
  invalidMessage: string;
  invalid?: boolean;
  disabled?: boolean;
  onCountryChange: (country: SupportedPhoneCountry) => void;
  onNationalNumberChange: (value: string) => void;
};

export function CountryPhoneInput({
  country,
  nationalNumber,
  locale,
  countryLabel,
  numberLabel,
  help,
  invalidMessage,
  invalid = false,
  disabled = false,
  onCountryChange,
  onNationalNumberChange,
}: CountryPhoneInputProps) {
  const callingCode = phoneCountry(country).callingCode;
  return (
    <fieldset className="space-y-2">
      <legend className="sr-only">{numberLabel}</legend>
      <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.25fr)]">
        <div>
          <Label htmlFor="phone-country">{countryLabel}</Label>
          <select
            id="phone-country"
            value={country}
            disabled={disabled}
            onChange={(event) => onCountryChange(event.target.value as SupportedPhoneCountry)}
            className="mt-1 flex h-9 w-full rounded-lg border border-input bg-transparent px-3 py-1 text-sm shadow-xs outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {SUPPORTED_PHONE_COUNTRIES.map((option) => (
              <option key={option.region} value={option.region}>
                {phoneCountryName(option.region, locale)} (+{option.callingCode})
              </option>
            ))}
          </select>
        </div>
        <div>
          <Label htmlFor="phone-national">{numberLabel}</Label>
          <div className="mt-1 flex rounded-lg border border-input bg-transparent shadow-xs transition-colors focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/50 has-[input[aria-invalid=true]]:border-destructive has-[input[aria-invalid=true]]:ring-3 has-[input[aria-invalid=true]]:ring-destructive/20">
            <span className="flex items-center border-r border-input px-3 text-sm text-muted-foreground" aria-hidden="true">
              +{callingCode}
            </span>
            <Input
              id="phone-national"
              type="tel"
              inputMode="tel"
              autoComplete="tel-national"
              value={nationalNumber}
              disabled={disabled}
              aria-invalid={invalid}
              aria-describedby={invalid ? "phone-error" : "phone-help"}
              onChange={(event) => onNationalNumberChange(event.target.value)}
              className="border-0 shadow-none focus-visible:ring-0"
            />
          </div>
        </div>
      </div>
      <p id="phone-help" className="text-xs text-muted-foreground">{help}</p>
      {invalid && <p id="phone-error" role="alert" className="text-sm text-red-600">{invalidMessage}</p>}
    </fieldset>
  );
}
