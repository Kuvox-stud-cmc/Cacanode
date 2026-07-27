"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { useAuthStore } from "@/components/providers/StoreProvider";
import { useApiClient } from "@/hooks/useApiClient";
import {
  getRecruitmentAvailability,
  getRecruitmentSettings,
  updateRecruitmentAvailability,
  updateRecruitmentSettings,
  type Availability,
  type RecruitmentSettings,
} from "@/lib/recruitment-admin-api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useLocaleChangeDraft } from "@/hooks/useLocaleChangeDraft";

import { useLocale } from "next-intl";
import { formatEnumLabel, TIMEZONE_OPTIONS } from "@/lib/recruitment-formatters";

export function RecruitmentSetupPage() {
  const t = useTranslations("Recruitment");
  const locale = useLocale();
  const role = useAuthStore((state) => state.user?.role);
  const tenantId = useAuthStore((state) => state.user?.tenantId);
  const { request } = useApiClient();
  const [settings, setSettings] = useState<RecruitmentSettings | null>(null);
  const [availability, setAvailability] = useState<Availability | null>(null);
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (role !== "TENANT_ADMIN") return;
    Promise.all([getRecruitmentSettings(request), getRecruitmentAvailability(request)])
      .then(([nextSettings, nextAvailability]) => {
        setSettings(nextSettings);
        setAvailability(nextAvailability);
      })
      .catch((cause) => setMessage(cause instanceof Error ? cause.message : t("loadError")));
  }, [request, role, t]);

  const clearLocaleDraft = useLocaleChangeDraft(
    `recruitment:setup:${tenantId ?? "unknown"}`,
    { settings, availability },
    (draft) => {
      setSettings(draft.settings);
      setAvailability(draft.availability);
    },
    Boolean(tenantId && settings && availability),
  );

  if (role !== "TENANT_ADMIN") {
    return <p role="alert" className="rounded-md border border-red-200 bg-red-50 p-4 text-red-800">{t("adminOnly")}</p>;
  }

  async function save() {
    if (!settings || !availability) return;
    setMessage("");
    try {
      const { version: ignoredVersion, ...value } = settings;
      void ignoredVersion;
      const [savedSettings, savedAvailability] = await Promise.all([
        updateRecruitmentSettings(request, value),
        updateRecruitmentAvailability(request, {
          version: availability.version,
          weeklyWindows: availability.weeklyWindows,
          exceptions: availability.exceptions,
        }),
      ]);
      clearLocaleDraft();
      setSettings(savedSettings);
      setAvailability(savedAvailability);
      setMessage(t("saved"));
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : t("loadError"));
    }
  }

  return <div className="space-y-4" aria-live="polite">
    <h3 className="text-lg font-semibold">{t("setup.title")}</h3>
    {settings && <Card>
      <CardHeader><CardTitle className="text-base">{t("setup.automation")}</CardTitle></CardHeader>
      <CardContent className="grid gap-4 sm:grid-cols-2">
        <Field label={t("setup.defaultAutomation")}>
          <Select value={settings.defaultAutomationMode} onValueChange={(value) => setSettings({ ...settings, defaultAutomationMode: value ?? settings.defaultAutomationMode })}>
            <SelectTrigger aria-label={t("setup.defaultAutomation")}><SelectValue>{formatEnumLabel(settings.defaultAutomationMode, locale)}</SelectValue></SelectTrigger>
            <SelectContent>{["MANUAL", "AUTO_INVITE_ALL", "AUTO_INVITE_MATCHING"].map((value) => <SelectItem key={value} value={value}>{formatEnumLabel(value, locale)}</SelectItem>)}</SelectContent>
          </Select>
        </Field>
        <Field label={t("setup.cvAiMode")}>
          <Select value={settings.cvAiMode} onValueChange={(value) => setSettings({ ...settings, cvAiMode: value ?? settings.cvAiMode })}>
            <SelectTrigger aria-label={t("setup.cvAiMode")}><SelectValue>{formatEnumLabel(settings.cvAiMode, locale)}</SelectValue></SelectTrigger>
            <SelectContent>{["OFF", "SUMMARY_ONLY", "PERSONALIZED_QUESTIONS"].map((value) => <SelectItem key={value} value={value}>{formatEnumLabel(value, locale)}</SelectItem>)}</SelectContent>
          </Select>
        </Field>
        <Field label={t("setup.timezone")}>
          <select
            className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm"
            aria-label={t("setup.timezone")}
            value={settings.schedulingTimezone}
            onChange={(event) => setSettings({ ...settings, schedulingTimezone: event.target.value })}
          >
            {TIMEZONE_OPTIONS.map((tz) => (
              <option key={tz.value} value={tz.value}>
                {tz.label}
              </option>
            ))}
          </select>
        </Field>
        <NumberField label={t("setup.notice")} value={settings.minimumNoticeMinutes} onChange={(value) => setSettings({ ...settings, minimumNoticeMinutes: value })} />
        <NumberField label={t("setup.horizon")} value={settings.bookingHorizonDays} onChange={(value) => setSettings({ ...settings, bookingHorizonDays: value })} />
        <NumberField label={t("setup.cutoff")} value={settings.rescheduleCutoffMinutes} onChange={(value) => setSettings({ ...settings, rescheduleCutoffMinutes: value })} />
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={settings.recordingEnabled} onChange={(event) => setSettings({ ...settings, recordingEnabled: event.target.checked, recordingRetentionDays: event.target.checked ? Math.max(1, settings.recordingRetentionDays) : 0 })} />
          {t("setup.recording")}
        </label>
        <NumberField label={t("setup.retention")} value={settings.recordingRetentionDays} disabled={!settings.recordingEnabled} onChange={(value) => setSettings({ ...settings, recordingRetentionDays: value })} />
      </CardContent>
    </Card>}
    {availability && <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="text-base">{t("setup.availability")}</CardTitle>
        <Button
          size="sm"
          variant="outline"
          onClick={() =>
            setAvailability({
              ...availability,
              weeklyWindows: [...availability.weeklyWindows, { dayOfWeek: 1, startLocal: "09:00", endLocal: "17:00" }],
            })
          }
        >
          + Add Window
        </Button>
      </CardHeader>
      <CardContent className="space-y-2">
        {availability.weeklyWindows.map((window, index) => <div key={`${window.dayOfWeek}-${index}`} className="grid grid-cols-4 gap-2 items-center">
          <Input aria-label={`${t("setup.dayOfWeek")} ${index + 1}`} type="number" min={1} max={7} value={window.dayOfWeek} onChange={(event) => setAvailability({ ...availability, weeklyWindows: availability.weeklyWindows.map((item, itemIndex) => itemIndex === index ? { ...item, dayOfWeek: Number(event.target.value) } : item) })} />
          <Input aria-label={`${t("setup.startTime")} ${index + 1}`} type="time" value={window.startLocal} onChange={(event) => setAvailability({ ...availability, weeklyWindows: availability.weeklyWindows.map((item, itemIndex) => itemIndex === index ? { ...item, startLocal: event.target.value } : item) })} />
          <Input aria-label={`${t("setup.endTime")} ${index + 1}`} type="time" value={window.endLocal} onChange={(event) => setAvailability({ ...availability, weeklyWindows: availability.weeklyWindows.map((item, itemIndex) => itemIndex === index ? { ...item, endLocal: event.target.value } : item) })} />
          <Button
            size="sm"
            variant="ghost"
            className="text-red-600 hover:bg-red-50"
            onClick={() =>
              setAvailability({
                ...availability,
                weeklyWindows: availability.weeklyWindows.filter((_, itemIndex) => itemIndex !== index),
              })
            }
          >
            Remove
          </Button>
        </div>)}
      </CardContent>
    </Card>}
    <div className="flex items-center gap-3">
      <Button onClick={() => void save()} disabled={!settings}>{t("save")}</Button>
      {message && <span className="text-sm text-slate-600">{message}</span>}
    </div>
  </div>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="space-y-1.5"><Label>{label}</Label>{children}</div>;
}

function NumberField({ label, value, disabled, onChange }: { label: string; value: number; disabled?: boolean; onChange: (value: number) => void }) {
  return <Field label={label}><Input aria-label={label} type="number" disabled={disabled} value={value} onChange={(event) => onChange(Number(event.target.value))} /></Field>;
}
