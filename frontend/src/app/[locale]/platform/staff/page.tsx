"use client"

import { useCallback, useEffect, useState } from "react"
import { useFormatter, useTranslations } from "next-intl"
import { useSearchParams } from "next/navigation"
import { Loader2, Plus, RefreshCw } from "lucide-react"
import { useRouter } from "@/i18n/navigation"
import { useApiClient } from "@/hooks/useApiClient"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  cancelPlatformInvitation,
  invitePlatformStaff,
  listPlatformInvitations,
  listPlatformStaff,
  resendPlatformInvitation,
  updatePlatformStaffStatus,
  type PlatformInvitationItem,
  type PlatformPage,
  type PlatformStaffItem,
} from "@/lib/platform-staff-api"

type Tab = "staff" | "invitations"
type Row = PlatformStaffItem | PlatformInvitationItem
type Translate = (key: string, values?: Record<string, string | number>) => string

export default function PlatformStaffPage() {
  const t = useTranslations("Platform.staff")
  const translate: Translate = (key, values) => t(key as never, values as never)
  const format = useFormatter()
  const params = useSearchParams()
  const router = useRouter()
  const { request } = useApiClient()
  const tab: Tab = params.get("tab") === "invitations" ? "invitations" : "staff"
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0)
  const q = params.get("q") ?? ""
  const status = params.get("status") ?? ""
  const sort = params.get("sort") ?? "createdAt"
  const direction = params.get("direction") === "asc" ? "asc" : "desc"
  const [search, setSearch] = useState(q)
  const [data, setData] = useState<PlatformPage<Row> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [inviteOpen, setInviteOpen] = useState(false)
  const [email, setEmail] = useState("")
  const [busy, setBusy] = useState("")

  const updateUrl = useCallback((updates: Record<string, string | number>) => {
    const next = new URLSearchParams(params.toString())
    Object.entries(updates).forEach(([key, value]) => {
      if (value === "") next.delete(key)
      else next.set(key, String(value))
    })
    router.replace(`/platform/staff?${next.toString()}`)
  }, [params, router])

  const load = useCallback(async () => {
    setLoading(true)
    setError("")
    try {
      const query = { page, size: 20, q, status, sort, direction } as const
      const result = tab === "staff"
        ? await listPlatformStaff(request, query)
        : await listPlatformInvitations(request, query)
      setData(result as PlatformPage<Row>)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("loadError"))
    } finally {
      setLoading(false)
    }
  }, [direction, page, q, request, sort, status, t, tab])

  useEffect(() => {
    const task = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(task)
  }, [load])

  async function perform(id: string, task: () => Promise<unknown>) {
    setBusy(id)
    setError("")
    try {
      await task()
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("actionError"))
    } finally {
      setBusy("")
    }
  }

  async function submitInvite(event: React.FormEvent) {
    event.preventDefault()
    setBusy("invite")
    try {
      await invitePlatformStaff(request, email)
      setInviteOpen(false)
      setEmail("")
      updateUrl({ tab: "invitations", page: 0 })
      await load()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("actionError"))
    } finally {
      setBusy("")
    }
  }

  const statuses = tab === "staff"
    ? ["", "ACTIVE", "INACTIVE", "SUSPENDED"]
    : ["", "PENDING", "EXPIRED", "CANCELLED", "ACCEPTED"]
  const sorts = tab === "staff"
    ? ["name", "email", "status", "createdAt", "lastLoginAt"]
    : ["email", "status", "createdAt", "expiresAt", "lastSentAt"]
  const totalPages = Math.max(1, Math.ceil((data?.total ?? 0) / 20))

  return (
    <div className="mx-auto max-w-7xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div><h1 className="text-2xl font-bold text-slate-950">{t("title")}</h1><p className="mt-1 text-sm text-slate-600">{t("description")}</p></div>
        <div className="flex gap-2"><Button variant="outline" onClick={() => void load()} disabled={loading}><RefreshCw className="size-4" />{t("refresh")}</Button><Button onClick={() => setInviteOpen(true)}><Plus className="size-4" />{t("invite")}</Button></div>
      </div>

      <section className="rounded-xl border bg-white">
        <div className="flex border-b">
          {(["staff", "invitations"] as Tab[]).map(value => <button key={value} type="button" onClick={() => updateUrl({ tab: value, page: 0, status: "" })} className={`px-5 py-3 text-sm font-medium ${tab === value ? "border-b-2 border-indigo-600 text-indigo-700" : "text-slate-500"}`}>{t(`tabs.${value}`)}</button>)}
        </div>
        <form className="flex flex-wrap gap-3 border-b p-4" onSubmit={event => { event.preventDefault(); updateUrl({ q: search.trim().slice(0, 200), page: 0 }) }}>
          <Input value={search} onChange={event => setSearch(event.target.value)} className="max-w-sm" placeholder={t("search")} />
          <select value={status} onChange={event => updateUrl({ status: event.target.value, page: 0 })} className="rounded-md border px-3 text-sm">{statuses.map(value => <option key={value} value={value}>{value || t("allStatuses")}</option>)}</select>
          <select value={sort} onChange={event => updateUrl({ sort: event.target.value, page: 0 })} className="rounded-md border px-3 text-sm">{sorts.map(value => <option key={value} value={value}>{translate(`sort.${value}`)}</option>)}</select>
          <select value={direction} onChange={event => updateUrl({ direction: event.target.value, page: 0 })} className="rounded-md border px-3 text-sm"><option value="asc">{t("ascending")}</option><option value="desc">{t("descending")}</option></select>
          <Button type="submit" variant="outline">{t("apply")}</Button>
        </form>
        {error && <p role="alert" className="m-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
        {loading ? <div className="grid h-56 place-items-center"><Loader2 className="size-7 animate-spin text-indigo-600" /></div> : (data?.items.length ?? 0) === 0 ? <p className="p-14 text-center text-slate-500">{t("empty")}</p> : <StaffTable tab={tab} rows={data!.items} busy={busy} request={request} perform={perform} formatDate={value => format.dateTime(new Date(value), { dateStyle: "medium" })} t={translate} />}
        <div className="flex items-center justify-between border-t p-4 text-sm"><span>{t("total", { count: data?.total ?? 0 })}</span><div className="flex items-center gap-2"><Button size="sm" variant="outline" disabled={page === 0} onClick={() => updateUrl({ page: page - 1 })}>{t("previous")}</Button><span>{page + 1}/{totalPages}</span><Button size="sm" variant="outline" disabled={page + 1 >= totalPages} onClick={() => updateUrl({ page: page + 1 })}>{t("next")}</Button></div></div>
      </section>

      {inviteOpen && <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/55 p-4" role="dialog" aria-modal="true" aria-labelledby="invite-title"><form className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl" onSubmit={submitInvite}><h2 id="invite-title" className="text-xl font-bold">{t("dialog.title")}</h2><div className="mt-5 space-y-2"><Label htmlFor="platform-email">{t("dialog.email")}</Label><Input id="platform-email" required type="email" value={email} onChange={event => setEmail(event.target.value)} /></div><div className="mt-4 rounded-lg bg-slate-50 p-3 text-sm"><span className="text-slate-500">{t("dialog.role")}: </span><strong>{t("platformAdmin")}</strong></div><div className="mt-6 flex justify-end gap-2"><Button type="button" variant="outline" onClick={() => setInviteOpen(false)}>{t("dialog.close")}</Button><Button type="submit" disabled={busy === "invite"}>{t("dialog.send")}</Button></div></form></div>}
    </div>
  )
}

function StaffTable({ tab, rows, busy, request, perform, formatDate, t }: {
  tab: Tab; rows: Row[]; busy: string; request: ReturnType<typeof useApiClient>["request"]
  perform: (id: string, task: () => Promise<unknown>) => Promise<void>
  formatDate: (value: string) => string; t: Translate
}) {
  return <div className="overflow-x-auto"><table className="w-full min-w-[850px] text-left text-sm"><thead className="bg-slate-50 text-slate-500"><tr><th className="px-4 py-3">{t("columns.person")}</th><th className="px-4 py-3">{t("columns.role")}</th><th className="px-4 py-3">{t("columns.status")}</th><th className="px-4 py-3">{t("columns.created")}</th><th className="px-4 py-3">{t(tab === "staff" ? "columns.lastLogin" : "columns.expires")}</th><th className="px-4 py-3">{t("columns.actions")}</th></tr></thead><tbody>{rows.map(row => <tr key={row.id} className="border-t"><td className="px-4 py-3"><p className="font-medium text-slate-900">{"name" in row ? row.name : row.email}</p><p className="text-xs text-slate-500">{"name" in row ? row.email : t("invitation")}</p></td><td className="px-4 py-3">{t("platformAdmin")}</td><td className="px-4 py-3">{row.status}</td><td className="px-4 py-3">{formatDate(row.createdAt)}</td><td className="px-4 py-3">{"lastLoginAt" in row ? (row.lastLoginAt ? formatDate(row.lastLoginAt) : "—") : formatDate(row.expiresAt)}</td><td className="px-4 py-3"><RowActions row={row} busy={busy} request={request} perform={perform} t={t} /></td></tr>)}</tbody></table></div>
}

function RowActions({ row, busy, request, perform, t }: {
  row: Row; busy: string; request: ReturnType<typeof useApiClient>["request"]
  perform: (id: string, task: () => Promise<unknown>) => Promise<void>; t: Translate
}) {
  if ("currentUser" in row) {
    const next = row.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
    return <Button size="sm" variant="outline" disabled={row.currentUser || row.status === "SUSPENDED" || busy === row.id} onClick={() => { if (window.confirm(t(next === "ACTIVE" ? "confirm.active" : "confirm.inactive"))) void perform(row.id, () => updatePlatformStaffStatus(request, row.id, next)) }}>{row.status === "ACTIVE" ? t("deactivate") : t("reactivate")}</Button>
  }
  return <div className="flex gap-2"><Button size="sm" variant="outline" disabled={busy === row.id || !["PENDING", "EXPIRED"].includes(row.status)} onClick={() => void perform(row.id, () => resendPlatformInvitation(request, row.id))}>{t("resend")}</Button><Button size="sm" variant="destructive" disabled={busy === row.id || row.status !== "PENDING"} onClick={() => { if (window.confirm(t("confirm.cancel"))) void perform(row.id, () => cancelPlatformInvitation(request, row.id)) }}>{t("cancel")}</Button></div>
}
