"use client";

import { Search, SlidersHorizontal, X } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";

export function SearchField({
  value,
  onChange,
  placeholder = "Search",
  label = "Search records",
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  label?: string;
}) {
  return (
    <div className="relative min-w-0 flex-1">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
      <Input
        aria-label={label}
        value={value}
        maxLength={200}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="pl-9"
      />
    </div>
  );
}

export function UrlSearchField({
  initialValue,
  onDebouncedChange,
  placeholder,
  label,
}: {
  initialValue: string;
  onDebouncedChange: (value: string) => void;
  placeholder?: string;
  label?: string;
}) {
  const [value, setValue] = useState(initialValue);
  const debounced = useDebouncedValue(value, 300);
  useEffect(() => {
    if (debounced !== initialValue) onDebouncedChange(debounced);
  }, [debounced, initialValue, onDebouncedChange]);
  return <SearchField value={value} onChange={setValue} placeholder={placeholder} label={label} />;
}

export function FilterSelect({
  label,
  value,
  onChange,
  children,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  children: ReactNode;
}) {
  return (
    <label className="min-w-36 space-y-1 text-xs font-medium text-slate-500">
      <span>{label}</span>
      <select
        className="h-10 w-full rounded-md border border-slate-200 bg-white px-2 text-sm font-normal text-slate-700 outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {children}
      </select>
    </label>
  );
}

export function DateRangeFields({
  from,
  to,
  onFromChange,
  onToChange,
  prefix = "Date",
}: {
  from: string;
  to: string;
  onFromChange: (value: string) => void;
  onToChange: (value: string) => void;
  prefix?: string;
}) {
  return (
    <div className="grid grid-cols-2 gap-2">
      <Label className="space-y-1 text-xs text-slate-500">
        <span>{prefix} from</span>
        <Input type="date" value={from} onChange={(event) => onFromChange(event.target.value)} />
      </Label>
      <Label className="space-y-1 text-xs text-slate-500">
        <span>{prefix} to</span>
        <Input type="date" min={from || undefined} value={to} onChange={(event) => onToChange(event.target.value)} />
      </Label>
    </div>
  );
}

export function FilterPanel({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
      <SlidersHorizontal className="mb-3 hidden size-4 text-slate-400 sm:block" aria-hidden="true" />
      {children}
    </div>
  );
}

export function ClearFiltersButton({ onClick, disabled }: { onClick: () => void; disabled?: boolean }) {
  return (
    <Button type="button" variant="ghost" size="sm" disabled={disabled} onClick={onClick} className="gap-1.5">
      <X className="size-3.5" /> Clear
    </Button>
  );
}

export function ResultsSummary({ total, refreshing }: { total: number; refreshing?: boolean }) {
  return (
    <p className="text-sm text-slate-500" aria-live="polite">
      {total.toLocaleString()} {total === 1 ? "result" : "results"}
      {refreshing ? <span className="ml-2 text-indigo-500">Refreshing…</span> : null}
    </p>
  );
}

export function PaginationControls({
  page,
  size,
  total,
  onPageChange,
  onSizeChange,
}: {
  page: number;
  size: number;
  total: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <div className="flex flex-col gap-3 border-t border-slate-200 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
      <label className="flex items-center gap-2 text-sm text-slate-500">
        Rows
        <select
          aria-label="Rows per page"
          className="h-9 rounded-md border border-slate-200 bg-white px-2 text-sm"
          value={size}
          onChange={(event) => onSizeChange(Number(event.target.value))}
        >
          {[10, 20, 50].map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
      </label>
      <div className="flex items-center justify-between gap-3 sm:justify-end">
        <span className="text-sm text-slate-500">Page {page} of {pages}</span>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => onPageChange(page - 1)}>Previous</Button>
          <Button variant="outline" size="sm" disabled={page >= pages} onClick={() => onPageChange(page + 1)}>Next</Button>
        </div>
      </div>
    </div>
  );
}
