"use client"

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react"
import Image from "next/image"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { ArrowLeft, ArrowRight, BookOpen, LayoutDashboard, Menu, Search, X } from "lucide-react"
import { useAuthStore } from "@/components/providers/StoreProvider"
import { useTokenRehydration } from "@/hooks/useTokenRehydration"
import { documentationGroups, documentationPage, documentationPages } from "@/lib/documentation"
import { cn } from "@/lib/utils"

type SearchResult = { href: string; pageTitle: string; title: string; description: string }

function buildSearchResults(query: string): SearchResult[] {
  const needle = query.trim().toLowerCase()
  const results = documentationPages.flatMap((page) => {
    const pageResult: SearchResult = { href: page.href, pageTitle: page.title, title: page.title, description: page.description }
    const sectionResults = page.sections.map((section) => ({
      href: `${page.href}#${section.id}`,
      pageTitle: page.title,
      title: section.title,
      description: page.description,
    }))
    if (!needle) return [pageResult, ...sectionResults]
    const pageText = [page.title, page.description, ...page.keywords].join(" ").toLowerCase()
    const matches = pageText.includes(needle) ? [pageResult] : []
    return [...matches, ...sectionResults.filter((result, index) => [result.title, page.sections[index]?.keywords?.join(" ")].join(" ").toLowerCase().includes(needle))]
  })
  return results.slice(0, 12)
}

function SearchDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [query, setQuery] = useState("")
  const [activeIndex, setActiveIndex] = useState(0)
  const dialogRef = useRef<HTMLDivElement>(null)
  const router = useRouter()
  const results = useMemo(() => buildSearchResults(query), [query])

  function closeDialog() {
    setQuery("")
    setActiveIndex(0)
    onClose()
  }

  if (!open) return null
  return (
    <div className="fixed inset-0 z-[70] flex items-start justify-center bg-slate-950/55 px-4 pt-[10vh] backdrop-blur-sm" role="dialog" aria-modal="true" aria-label="Search documentation" onMouseDown={(event) => { if (event.currentTarget === event.target) closeDialog() }}>
      <div ref={dialogRef} className="w-full max-w-2xl overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl" onKeyDown={(event) => {
        if (event.key !== "Tab") return
        const focusable = dialogRef.current?.querySelectorAll<HTMLElement>('input, button, a[href]')
        if (!focusable?.length) return
        const first = focusable[0]
        const last = focusable[focusable.length - 1]
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
      }}>
        <div className="flex items-center gap-3 border-b border-slate-200 px-4">
          <Search className="size-5 text-slate-400" />
          <input
            autoFocus
            value={query}
            onChange={(event) => { setQuery(event.target.value); setActiveIndex(0) }}
            onKeyDown={(event) => {
              if (event.key === "Escape") closeDialog()
              if (event.key === "ArrowDown") { event.preventDefault(); setActiveIndex((value) => results.length ? Math.min(value + 1, results.length - 1) : 0) }
              if (event.key === "ArrowUp") { event.preventDefault(); setActiveIndex((value) => Math.max(value - 1, 0)) }
              if (event.key === "Enter" && results[activeIndex]) {
                router.push(results[activeIndex].href)
                closeDialog()
              }
            }}
            placeholder="Search guides, concepts, and API fields…"
            className="h-14 min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-slate-400"
          />
          <button type="button" onClick={closeDialog} className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Close search"><X className="size-5" /></button>
        </div>
        <div className="max-h-[60vh] overflow-y-auto p-2">
          {results.map((result, index) => (
            <Link
              key={result.href}
              href={result.href}
              onClick={closeDialog}
              onMouseEnter={() => setActiveIndex(index)}
              className={cn("block rounded-lg px-3 py-3", index === activeIndex ? "bg-indigo-50" : "hover:bg-slate-50")}
            >
              <span className="block text-sm font-semibold text-slate-900">{result.title}</span>
              <span className="mt-0.5 block text-xs text-slate-500">{result.pageTitle} · {result.description}</span>
            </Link>
          ))}
          {results.length === 0 && <p className="px-4 py-10 text-center text-sm text-slate-500">No documentation matches “{query}”.</p>}
        </div>
        <div className="border-t border-slate-200 bg-slate-50 px-4 py-2 text-xs text-slate-500">Use ↑ ↓ to navigate, Enter to open, and Esc to close.</div>
      </div>
    </div>
  )
}

function DocsNavigation({ pathname, onNavigate }: { pathname: string; onNavigate?: () => void }) {
  return (
    <nav aria-label="Documentation navigation" className="space-y-6">
      {documentationGroups.map((group) => (
        <div key={group}>
          <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-slate-400">{group}</p>
          <div className="space-y-1">
            {documentationPages.filter((page) => page.group === group).map((page) => {
              const active = pathname === page.href
              return <Link key={page.href} href={page.href} onClick={onNavigate} aria-current={active ? "page" : undefined} className={cn("block rounded-md px-3 py-2 text-sm transition-colors", active ? "bg-indigo-50 font-semibold text-indigo-700" : "text-slate-600 hover:bg-slate-100 hover:text-slate-950")}>{page.title}</Link>
            })}
          </div>
        </div>
      ))}
    </nav>
  )
}

export function DocumentationShell({ children }: { children: ReactNode }) {
  const pathname = usePathname()
  const authStatus = useTokenRehydration()
  const user = useAuthStore((state) => state.user)
  const current = documentationPage(pathname)
  const currentIndex = documentationPages.findIndex((page) => page.href === current.href)
  const previous = currentIndex > 0 ? documentationPages[currentIndex - 1] : null
  const next = currentIndex < documentationPages.length - 1 ? documentationPages[currentIndex + 1] : null
  const [searchOpen, setSearchOpen] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)

  useEffect(() => {
    function shortcut(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") { event.preventDefault(); setSearchOpen(true) }
      if (event.key === "Escape") { setSearchOpen(false); setMobileOpen(false) }
    }
    window.addEventListener("keydown", shortcut)
    return () => window.removeEventListener("keydown", shortcut)
  }, [])

  return (
    <div className="min-h-dvh bg-white text-slate-900">
      <header className="sticky top-0 z-50 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-[1500px] items-center gap-3 px-4 sm:px-6">
          <button type="button" onClick={() => setMobileOpen(true)} className="rounded-md p-2 text-slate-600 hover:bg-slate-100 lg:hidden" aria-label="Open documentation navigation"><Menu className="size-5" /></button>
          <Link href="/documentation" className="flex shrink-0 items-center gap-2">
            <Image src="/logo.png" alt="CacaNode" width={28} height={28} />
            <span className="font-bold">CacaNode</span><span className="hidden border-l border-slate-300 pl-2 text-sm text-slate-500 sm:inline">Docs</span>
          </Link>
          <button type="button" onClick={() => setSearchOpen(true)} className="mx-auto flex h-9 w-full max-w-md items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm text-slate-500 hover:border-slate-300 hover:bg-white">
            <Search className="size-4" /><span className="truncate">Search documentation</span><kbd className="ml-auto hidden rounded border bg-white px-1.5 py-0.5 font-sans text-[10px] text-slate-400 sm:inline">⌘K</kbd>
          </button>
          <nav className="hidden shrink-0 items-center gap-1 text-sm md:flex" aria-label="Documentation header">
            <Link href="/pricing" className="rounded-md px-3 py-2 text-slate-600 hover:bg-slate-100">Pricing</Link>
            {authStatus === "unauthenticated" && <Link href="/login" className="rounded-md px-3 py-2 text-slate-600 hover:bg-slate-100">Log in</Link>}
            {authStatus === "authenticated" && user && <span className="max-w-36 truncate rounded-md bg-slate-100 px-3 py-2 text-slate-600" title={user.fullName}>{user.fullName}</span>}
            {authStatus === "rehydrating" && <span className="h-9 w-20 animate-pulse rounded-md bg-slate-100" aria-label="Checking session" />}
            <Link href="/dashboard" className="flex items-center gap-2 rounded-md bg-indigo-600 px-3 py-2 font-medium text-white hover:bg-indigo-700"><LayoutDashboard className="size-4" />Dashboard</Link>
          </nav>
        </div>
      </header>

      <div className="mx-auto grid max-w-[1500px] grid-cols-1 lg:grid-cols-[250px_minmax(0,760px)] lg:gap-10 lg:px-6 xl:grid-cols-[250px_minmax(0,760px)_220px]">
        <aside className="sticky top-16 hidden h-[calc(100dvh-4rem)] overflow-y-auto border-r border-slate-200 py-8 pr-6 lg:block"><DocsNavigation pathname={pathname} /></aside>
        <main className="min-w-0 px-5 py-10 sm:px-8 lg:px-0 lg:py-12">
          {children}
          <nav className="mt-12 grid gap-3 border-t border-slate-200 pt-8 sm:grid-cols-2" aria-label="Previous and next documentation">
            {previous ? <Link href={previous.href} className="rounded-lg border border-slate-200 p-4 hover:border-indigo-300"><span className="flex items-center gap-2 text-xs text-slate-500"><ArrowLeft className="size-3.5" />Previous</span><span className="mt-1 block font-semibold text-slate-900">{previous.title}</span></Link> : <span />}
            {next && <Link href={next.href} className="rounded-lg border border-slate-200 p-4 text-right hover:border-indigo-300"><span className="flex items-center justify-end gap-2 text-xs text-slate-500">Next<ArrowRight className="size-3.5" /></span><span className="mt-1 block font-semibold text-slate-900">{next.title}</span></Link>}
          </nav>
        </main>
        <aside className="sticky top-16 hidden h-[calc(100dvh-4rem)] py-12 xl:block">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">On this page</p>
          <nav className="border-l border-slate-200" aria-label="On this page">
            {current.sections.map((section) => <Link key={section.id} href={`#${section.id}`} className="block border-l border-transparent px-4 py-1.5 text-sm leading-5 text-slate-500 hover:border-indigo-500 hover:text-indigo-700">{section.title}</Link>)}
          </nav>
        </aside>
      </div>

      {mobileOpen && <div className="fixed inset-0 z-[60] lg:hidden"><button type="button" className="absolute inset-0 bg-slate-950/50" onClick={() => setMobileOpen(false)} aria-label="Close navigation" /><aside className="relative h-full w-[min(86vw,330px)] overflow-y-auto bg-white p-5 shadow-xl"><div className="mb-6 flex items-center justify-between"><span className="flex items-center gap-2 font-semibold"><BookOpen className="size-5 text-indigo-600" />Documentation</span><button type="button" onClick={() => setMobileOpen(false)} className="rounded p-1.5 hover:bg-slate-100" aria-label="Close navigation"><X className="size-5" /></button></div><DocsNavigation pathname={pathname} onNavigate={() => setMobileOpen(false)} /><div className="mt-8 grid gap-2 border-t pt-5 text-sm"><Link href="/pricing">Pricing</Link>{authStatus === "unauthenticated" && <Link href="/login">Log in</Link>}{authStatus === "authenticated" && user && <p className="truncate text-slate-500">Signed in as {user.fullName}</p>}<Link href="/dashboard" className="font-medium text-indigo-700">Dashboard</Link></div></aside></div>}
      <SearchDialog open={searchOpen} onClose={() => setSearchOpen(false)} />
    </div>
  )
}
