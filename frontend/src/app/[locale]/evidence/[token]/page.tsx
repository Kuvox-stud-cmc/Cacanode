"use client";


import { useEffect, useMemo, useRef, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { useParams } from "next/navigation";
import { FileText, Loader2, ShieldCheck } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher";
import { getPublicEvidence, type PublicEvidence } from "@/lib/public-evidence-api";
import { cn } from "@/lib/utils";
import type { DocumentUnit } from "@/types";

function unitIdentifier(unit: DocumentUnit): string {
  return unit.unit_id ? `unit:${unit.unit_id}` : `chunk:${unit.chunk_index}`;
}

function UnitMetadata({ unit }: { unit: DocumentUnit }) {
  const t = useTranslations("Evidence");
  const labels = [
    unit.page_number ? t("page", { number: unit.page_number }) : null,
    unit.sheet_name ? t("sheet", { name: unit.sheet_name }) : null,
    unit.cell_range ? t("cells", { range: unit.cell_range }) : null,
  ].filter(Boolean);
  if (labels.length === 0) return null;
  return <div className="mb-3 flex flex-wrap gap-2">{labels.map((label) => <Badge key={label} variant="outline">{label}</Badge>)}</div>;
}

function IndexedUnit({ unit }: { unit: DocumentUnit }) {
  const blockType = unit.block_type ?? "paragraph";
  if (blockType === "heading") {
    return <div><UnitMetadata unit={unit} /><h2 className="text-lg font-semibold text-slate-900 sm:text-xl">{unit.text}</h2></div>;
  }
  if (blockType === "code") {
    return <div><UnitMetadata unit={unit} /><pre className="overflow-x-auto rounded-xl bg-slate-950 p-4 text-sm leading-6 text-slate-100"><code>{unit.text}</code></pre></div>;
  }
  if (blockType === "table" || blockType === "row") {
    return <div><UnitMetadata unit={unit} /><pre className="overflow-x-auto whitespace-pre-wrap rounded-xl border border-slate-200 bg-slate-50 p-4 font-sans text-sm leading-6 text-slate-700">{unit.text}</pre></div>;
  }
  if (blockType === "quote") {
    return <div><UnitMetadata unit={unit} /><blockquote className="border-l-4 border-indigo-200 pl-4 italic leading-7 text-slate-600">{unit.text}</blockquote></div>;
  }
  return <div><UnitMetadata unit={unit} /><p className="whitespace-pre-wrap text-[15px] leading-7 text-slate-700">{unit.text}</p></div>;
}

export default function PublicEvidencePage() {
  const t = useTranslations("Evidence");
  const format = useFormatter();
  const { token } = useParams<{ token: string }>();
  const refs = useRef(new Map<string, HTMLElement>());
  const [evidence, setEvidence] = useState<PublicEvidence | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const focusedUnit = useMemo(
    () => evidence?.units.find((unit) => unitIdentifier(unit) === evidence.focus) ?? null,
    [evidence],
  );

  useEffect(() => {
    let cancelled = false;
    getPublicEvidence(token)
      .then((result) => { if (!cancelled) setEvidence(result); })
      .catch((caught) => { if (!cancelled) setError(caught instanceof Error ? caught.message : t("fallbackUnavailable")); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [t, token]);

  useEffect(() => {
    if (!focusedUnit) return;
    const timer = window.setTimeout(() => {
      refs.current.get(unitIdentifier(focusedUnit))?.scrollIntoView({ behavior: "smooth", block: "center" });
    }, 100);
    return () => window.clearTimeout(timer);
  }, [focusedUnit]);

  if (loading) {
    return <main className="grid min-h-screen place-items-center bg-slate-50"><div className="fixed right-4 top-4"><LanguageSwitcher /></div><Loader2 className="size-8 animate-spin text-indigo-600" aria-label={t("loading")} /></main>;
  }

  if (!evidence) {
    return <main className="grid min-h-screen place-items-center bg-slate-50 p-6"><div className="fixed right-4 top-4"><LanguageSwitcher /></div><Card className="max-w-lg"><CardContent className="py-14 text-center"><FileText className="mx-auto size-10 text-slate-400" /><h1 className="mt-4 text-lg font-semibold text-slate-900">{t("unavailable")}</h1><p className="mt-2 text-sm leading-6 text-slate-500">{error ?? t("invalidLink")}</p></CardContent></Card></main>;
  }

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-8 sm:px-6 sm:py-12">
      <div className="mx-auto max-w-5xl space-y-5">
        <div className="flex justify-end"><LanguageSwitcher /></div>
        <header className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
          <div className="flex items-start gap-4"><span className="grid size-11 shrink-0 place-items-center rounded-xl bg-indigo-50 text-indigo-600"><FileText className="size-5" /></span><div className="min-w-0"><p className="text-xs font-semibold uppercase tracking-wider text-indigo-600">{t("customerVisible")}</p><h1 className="mt-1 break-words text-xl font-semibold text-slate-900">{evidence.source_name}</h1><p className="mt-2 flex items-center gap-2 text-xs leading-5 text-slate-500"><ShieldCheck className="size-4 text-emerald-600" />{t("expires", { date: format.dateTime(new Date(evidence.expires_at), { dateStyle: "medium", timeStyle: "short" }) })}</p></div></div>
        </header>
        <Card><CardContent className="space-y-3 p-3 sm:p-8">{evidence.units.map((unit) => {
          const identifier = unitIdentifier(unit);
          const focused = evidence.focus === identifier;
          return <section key={identifier} ref={(element) => { if (element) refs.current.set(identifier, element); else refs.current.delete(identifier); }} className={cn("scroll-mt-8 rounded-xl border border-transparent px-3 py-4 transition sm:px-4", focused && "border-indigo-300 bg-indigo-50 ring-4 ring-indigo-100")}><IndexedUnit unit={unit} /></section>;
        })}</CardContent></Card>
      </div>
    </main>
  );
}
