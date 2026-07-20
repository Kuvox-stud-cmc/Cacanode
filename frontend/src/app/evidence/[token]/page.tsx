"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { FileText, Loader2, ShieldCheck } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { getPublicEvidence, type PublicEvidence } from "@/lib/public-evidence-api";
import { cn } from "@/lib/utils";
import type { DocumentUnit } from "@/types";

function unitIdentifier(unit: DocumentUnit): string {
  return unit.unit_id ? `unit:${unit.unit_id}` : `chunk:${unit.chunk_index}`;
}

function UnitMetadata({ unit }: { unit: DocumentUnit }) {
  const labels = [
    unit.page_number ? `Page ${unit.page_number}` : null,
    unit.sheet_name ? `Sheet ${unit.sheet_name}` : null,
    unit.cell_range ? `Cells ${unit.cell_range}` : null,
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
      .catch((caught) => { if (!cancelled) setError(caught instanceof Error ? caught.message : "Evidence is unavailable"); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [token]);

  useEffect(() => {
    if (!focusedUnit) return;
    const timer = window.setTimeout(() => {
      refs.current.get(unitIdentifier(focusedUnit))?.scrollIntoView({ behavior: "smooth", block: "center" });
    }, 100);
    return () => window.clearTimeout(timer);
  }, [focusedUnit]);

  if (loading) {
    return <main className="grid min-h-screen place-items-center bg-slate-50"><Loader2 className="size-8 animate-spin text-indigo-600" /></main>;
  }

  if (!evidence) {
    return <main className="grid min-h-screen place-items-center bg-slate-50 p-6"><Card className="max-w-lg"><CardContent className="py-14 text-center"><FileText className="mx-auto size-10 text-slate-400" /><h1 className="mt-4 text-lg font-semibold text-slate-900">Evidence unavailable</h1><p className="mt-2 text-sm leading-6 text-slate-500">{error ?? "This link is invalid, expired, or its widget token was revoked."}</p></CardContent></Card></main>;
  }

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-8 sm:px-6 sm:py-12">
      <div className="mx-auto max-w-5xl space-y-5">
        <header className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
          <div className="flex items-start gap-4"><span className="grid size-11 shrink-0 place-items-center rounded-xl bg-indigo-50 text-indigo-600"><FileText className="size-5" /></span><div className="min-w-0"><p className="text-xs font-semibold uppercase tracking-wider text-indigo-600">Customer-visible source</p><h1 className="mt-1 break-words text-xl font-semibold text-slate-900">{evidence.source_name}</h1><p className="mt-2 flex items-center gap-2 text-xs leading-5 text-slate-500"><ShieldCheck className="size-4 text-emerald-600" />This read-only link shows indexed content and expires {new Date(evidence.expires_at).toLocaleString()}.</p></div></div>
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
