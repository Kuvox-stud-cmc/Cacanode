"use client"

import {
  useRef,
  useState,
  type ChangeEvent,
  type FormEvent,
  type KeyboardEvent,
} from "react"
import Image from "next/image"
import Link from "next/link"
import {
  ArrowUp,
  FileText,
  Loader2,
  Paperclip,
  Plus,
  Sparkles,
  Type,
  Upload,
  X,
} from "lucide-react"
import { AppShell } from "@/components/app/AppShell"
import { useTokenRehydration } from "@/hooks/useTokenRehydration"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { cn } from "@/lib/utils"

type FileSource = {
  id: string
  kind: "file"
  name: string
  size: number
  fileType: string
}

type TextSource = {
  id: string
  kind: "text"
  name: string
  text: string
}

type PlaygroundSource = FileSource | TextSource

type UserMessage = {
  id: string
  role: "user"
  content: string
}

function makeId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function Playground({ authenticated }: { authenticated: boolean }) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const suppressRestoredComposerFocus = useRef(false)
  const [message, setMessage] = useState("")
  const [messages, setMessages] = useState<UserMessage[]>([])
  const [sources, setSources] = useState<PlaygroundSource[]>([])
  const [sourceMenuOpen, setSourceMenuOpen] = useState(false)
  const [authDialogOpen, setAuthDialogOpen] = useState(false)
  const [pasteDialogOpen, setPasteDialogOpen] = useState(false)
  const [pastedText, setPastedText] = useState("")

  function requireAuthentication(): boolean {
    if (authenticated) return false
    setSourceMenuOpen(false)
    setAuthDialogOpen(true)
    return true
  }

  function handleComposerFocus() {
    if (suppressRestoredComposerFocus.current) {
      suppressRestoredComposerFocus.current = false
      return
    }
    requireAuthentication()
  }

  function handleAuthDialogOpenChange(open: boolean) {
    if (!open) {
      suppressRestoredComposerFocus.current = true
      window.setTimeout(() => {
        suppressRestoredComposerFocus.current = false
      }, 250)
    }
    setAuthDialogOpen(open)
  }

  function submitMessage(event?: FormEvent) {
    event?.preventDefault()
    if (requireAuthentication()) return
    const content = message.trim()
    if (!content) return
    setMessages((current) => [
      ...current,
      { id: makeId(), role: "user", content },
    ])
    setMessage("")
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      submitMessage()
    }
  }

  function chooseUpload() {
    if (requireAuthentication()) return
    setSourceMenuOpen(false)
    fileInputRef.current?.click()
  }

  function handleFiles(event: ChangeEvent<HTMLInputElement>) {
    const selectedFiles = Array.from(event.target.files ?? [])
    if (selectedFiles.length === 0) return
    setSources((current) => [
      ...current,
      ...selectedFiles.map((file): FileSource => ({
        id: makeId(),
        kind: "file",
        name: file.name,
        size: file.size,
        fileType: file.type || "Unknown type",
      })),
    ])
    event.target.value = ""
  }

  function choosePasteText() {
    if (requireAuthentication()) return
    setSourceMenuOpen(false)
    setPasteDialogOpen(true)
  }

  function addPastedText() {
    const text = pastedText.trim()
    if (!text) return
    setSources((current) => [
      ...current,
      {
        id: makeId(),
        kind: "text",
        name: `Pasted text ${current.filter((source) => source.kind === "text").length + 1}`,
        text,
      },
    ])
    setPastedText("")
    setPasteDialogOpen(false)
  }

  return (
    <div
      className={cn(
        "flex min-h-[32rem] flex-col bg-white",
        authenticated ? "h-[calc(100dvh-3.5rem)]" : "h-dvh",
      )}
    >
      {!authenticated && (
        <header className="flex h-14 shrink-0 items-center border-b border-slate-200 px-4 sm:px-6">
          <Link href="/" className="flex items-center gap-2">
            <Image src="/logo.png" alt="CacaNode" width={28} height={28} />
            <span className="font-semibold text-slate-900">CacaNode Chat</span>
          </Link>
          <Button
            variant="outline"
            className="ml-auto"
            nativeButton={false}
            render={<Link href="/login?next=%2F" />}
          >
            Sign in
          </Button>
        </header>
      )}

      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto flex min-h-full w-full max-w-3xl flex-col px-4 py-8 sm:px-6">
          {messages.length === 0 ? (
            <div className="m-auto flex max-w-lg flex-col items-center py-12 text-center">
              <div className="mb-5 grid size-14 place-items-center rounded-2xl bg-indigo-50 text-indigo-600">
                <Sparkles className="size-7" />
              </div>
              <h2 className="text-2xl font-semibold tracking-tight text-slate-900">
                Chat with your documents
              </h2>
              <p className="mt-2 text-sm leading-6 text-slate-500 sm:text-base">
                Add a file or paste text, then ask questions about your source material.
              </p>
              {!authenticated && (
                <p className="mt-4 rounded-full bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-600">
                  Sign in to start a conversation
                </p>
              )}
            </div>
          ) : (
            <div className="flex flex-1 flex-col justify-end gap-5 py-4">
              {messages.map((item) => (
                <div key={item.id} className="flex justify-end">
                  <div className="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-md bg-indigo-600 px-4 py-3 text-sm leading-6 text-white sm:max-w-[75%]">
                    {item.content}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="shrink-0 border-t border-slate-100 bg-white px-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3 sm:px-6 sm:pb-5">
        <form onSubmit={submitMessage} className="mx-auto w-full max-w-3xl">
          {sources.length > 0 && (
            <div className="mb-2 flex gap-2 overflow-x-auto pb-1">
              {sources.map((source) => (
                <div
                  key={source.id}
                  className="flex min-w-0 max-w-64 shrink-0 items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 py-2 pl-2.5 pr-1.5"
                  title={source.kind === "file" ? `${source.name} · ${source.fileType}` : source.text}
                >
                  <div className="grid size-8 shrink-0 place-items-center rounded-md bg-white text-indigo-600 shadow-sm">
                    {source.kind === "file" ? <FileText className="size-4" /> : <Type className="size-4" />}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-xs font-medium text-slate-800">{source.name}</p>
                    <p className="truncate text-[11px] text-slate-500">
                      {source.kind === "file" ? formatBytes(source.size) : `${source.text.length} characters`}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setSources((current) => current.filter((item) => item.id !== source.id))}
                    className="ml-1 rounded-md p-1 text-slate-400 hover:bg-slate-200 hover:text-slate-700"
                    aria-label={`Remove ${source.name}`}
                  >
                    <X className="size-3.5" />
                  </button>
                </div>
              ))}
            </div>
          )}

          <div className="relative rounded-2xl border border-slate-300 bg-white p-2 shadow-[0_8px_30px_rgba(15,23,42,0.08)] focus-within:border-indigo-400 focus-within:ring-2 focus-within:ring-indigo-100">
            <textarea
              value={message}
              readOnly={!authenticated}
              onFocus={handleComposerFocus}
              onChange={(event) => authenticated && setMessage(event.target.value)}
              onKeyDown={handleComposerKeyDown}
              rows={2}
              placeholder="Ask a question about your sources..."
              className="block max-h-36 min-h-14 w-full resize-none bg-transparent px-2 py-1.5 text-sm leading-6 text-slate-900 outline-none placeholder:text-slate-400"
              aria-label="Message"
            />
            <div className="flex h-9 items-center justify-between gap-2">
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setSourceMenuOpen((open) => !open)}
                  className="flex h-9 items-center gap-1.5 rounded-lg px-2.5 text-sm text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                  aria-expanded={sourceMenuOpen}
                >
                  <Paperclip className="size-4" />
                  <span>Add source</span>
                </button>
                {sourceMenuOpen && (
                  <div className="absolute bottom-11 left-0 z-20 w-44 rounded-xl border border-slate-200 bg-white p-1.5 shadow-xl">
                    <button type="button" onClick={chooseUpload} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100">
                      <Upload className="size-4" /> Upload file
                    </button>
                    <button type="button" onClick={choosePasteText} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100">
                      <Type className="size-4" /> Paste text
                    </button>
                  </div>
                )}
                <input ref={fileInputRef} type="file" multiple className="hidden" onChange={handleFiles} />
              </div>
              <button
                type="submit"
                disabled={authenticated && !message.trim()}
                className={cn(
                  "grid size-9 shrink-0 place-items-center rounded-lg bg-indigo-600 text-white transition-colors hover:bg-indigo-700",
                  authenticated && !message.trim() && "cursor-not-allowed bg-slate-200 text-slate-400 hover:bg-slate-200",
                )}
                aria-label="Send message"
              >
                <ArrowUp className="size-4" />
              </button>
            </div>
          </div>
          <p className="mt-2 text-center text-[11px] text-slate-400">
            Sources and messages stay in this browser tab and are not uploaded.
          </p>
        </form>
      </div>

      <Dialog open={authDialogOpen} onOpenChange={handleAuthDialogOpenChange}>
        <DialogContent>
          <DialogHeader>
            <div className="mb-1 grid size-10 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
              <Sparkles className="size-5" />
            </div>
            <DialogTitle>Sign in to start chatting</DialogTitle>
            <DialogDescription>
              Create an account or sign in to attach sources and send messages.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="sm:grid sm:grid-cols-2">
            <Button
              variant="outline"
              nativeButton={false}
              render={<Link href="/register?next=%2F" />}
            >
              Create account
            </Button>
            <Button
              nativeButton={false}
              className="bg-indigo-600 text-white hover:bg-indigo-700"
              render={<Link href="/login?next=%2F" />}
            >
              Sign in
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={pasteDialogOpen} onOpenChange={setPasteDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Paste text</DialogTitle>
            <DialogDescription>Add text to use as a local source for this conversation.</DialogDescription>
          </DialogHeader>
          <textarea
            value={pastedText}
            onChange={(event) => setPastedText(event.target.value)}
            rows={9}
            autoFocus
            placeholder="Paste your source text here..."
            className="w-full resize-y rounded-lg border border-slate-300 p-3 text-sm leading-6 outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setPasteDialogOpen(false)}>Cancel</Button>
            <Button disabled={!pastedText.trim()} onClick={addPastedText} className="bg-indigo-600 text-white hover:bg-indigo-700">
              <Plus className="size-4" /> Add source
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

export default function ChatPlayground() {
  const status = useTokenRehydration()

  if (status === "rehydrating") {
    return (
      <div className="grid min-h-dvh place-items-center bg-white">
        <Loader2 className="size-8 animate-spin text-indigo-600" aria-label="Loading" />
      </div>
    )
  }

  if (status === "authenticated") {
    return (
      <AppShell contentClassName="p-0">
        <Playground authenticated />
      </AppShell>
    )
  }

  return <Playground authenticated={false} />
}
