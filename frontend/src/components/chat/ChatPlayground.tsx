"use client"

import {
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type FormEvent,
  type KeyboardEvent,
} from "react"
import Image from "next/image"
import Link from "next/link"
import toast from "react-hot-toast"
import {
  ArrowUp,
  FileText,
  Loader2,
  Paperclip,
  Sparkles,
  Square,
  Upload,
  X,
} from "lucide-react"
import { AppShell } from "@/components/app/AppShell"
import { useTokenRehydration } from "@/hooks/useTokenRehydration"
import { useApiClient } from "@/hooks/useApiClient"
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
import {
  fileTypeFromName,
  getDocumentStatusApi,
  isTerminalDocumentStatus,
  listDocumentsApi,
  uploadDocumentApi,
} from "@/lib/documents-api"
import {
  createChatSessionApi,
  getChatMessagesApi,
  submitChatMessageApi,
} from "@/lib/chat-api"
import { getTenantWorkspaceApi } from "@/lib/workspace-api"
import type { ChatCitation, Document, DocumentStatus, TenantWorkspace } from "@/types"

type SourceStatus = DocumentStatus | "UPLOADING"

type SourceDocument = Omit<Document, "status"> & {
  localId: string
  status: SourceStatus
}

type ChatMessage = {
  id: string
  role: "user" | "assistant"
  content: string
  citations?: ChatCitation[]
  loading?: boolean
  error?: boolean
}

type PersistedPlaygroundState = {
  sessionId: string | null
  messages: ChatMessage[]
  sources: SourceDocument[]
}

function playgroundStateKey(workspace: TenantWorkspace): string {
  return [
    "cacanode.chat.playground",
    workspace.tenantId,
    workspace.knowledgeBase.id,
    workspace.chatbot.id,
  ].join(".")
}

function emptyPlaygroundState(): PersistedPlaygroundState {
  return { sessionId: null, messages: [], sources: [] }
}

function readPersistedPlaygroundState(key: string): PersistedPlaygroundState {
  if (typeof window === "undefined") {
    return emptyPlaygroundState()
  }

  try {
    const raw = window.sessionStorage.getItem(key)
    if (!raw) return emptyPlaygroundState()

    const restored = JSON.parse(raw) as Partial<PersistedPlaygroundState>
    return {
      sessionId: typeof restored.sessionId === "string" ? restored.sessionId : null,
      messages: Array.isArray(restored.messages) ? restored.messages : [],
      sources: Array.isArray(restored.sources) ? restored.sources : [],
    }
  } catch {
    window.sessionStorage.removeItem(key)
    return emptyPlaygroundState()
  }
}

function makeId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function sourceFromDocument(document: Document): SourceDocument {
  return {
    ...document,
    localId: document.id,
  }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function statusLabel(status: SourceStatus): string {
  const labels: Record<SourceStatus, string> = {
    UPLOADING: "Uploading",
    PENDING: "Pending",
    PROCESSING: "Indexing",
    COMPLETED: "Ready",
    FAILED: "Failed",
  }
  return labels[status]
}

function statusClass(status: SourceStatus): string {
  const classes: Record<SourceStatus, string> = {
    UPLOADING: "bg-slate-100 text-slate-600",
    PENDING: "bg-amber-100 text-amber-800",
    PROCESSING: "bg-blue-100 text-blue-800",
    COMPLETED: "bg-emerald-100 text-emerald-800",
    FAILED: "bg-red-100 text-red-800",
  }
  return classes[status]
}

function isSupportedFile(file: File): boolean {
  const lower = file.name.toLowerCase()
  return lower.endsWith(".txt") || lower.endsWith(".pdf")
}

function Playground({ authenticated }: { authenticated: boolean }) {
  const { request } = useApiClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const suppressRestoredComposerFocus = useRef(false)
  const canPersistStateRef = useRef(false)
  const activeChatAbortRef = useRef<AbortController | null>(null)
  const [workspace, setWorkspace] = useState<TenantWorkspace | null>(null)
  const [message, setMessage] = useState("")
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [sources, setSources] = useState<SourceDocument[]>([])
  const [sourceMenuOpen, setSourceMenuOpen] = useState(false)
  const [authDialogOpen, setAuthDialogOpen] = useState(false)
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [sending, setSending] = useState(false)

  const hasCompletedSource = sources.some((source) => source.status === "COMPLETED")
  const hasIndexingSource = sources.some(
    (source) =>
      source.status === "UPLOADING" ||
      source.status === "PENDING" ||
      source.status === "PROCESSING",
  )

  useEffect(() => {
    if (!authenticated) return
    canPersistStateRef.current = false

    let cancelled = false
    getTenantWorkspaceApi(request)
      .then((tenantWorkspace) => {
        if (cancelled) return
        const restored = readPersistedPlaygroundState(playgroundStateKey(tenantWorkspace))
        setWorkspace(tenantWorkspace)
        setSessionId(restored.sessionId)
        setMessages(restored.messages)
        setSources(restored.sources)
        canPersistStateRef.current = true
      })
      .catch((error) => {
        if (!cancelled) {
          toast.error(error instanceof Error ? error.message : "Unable to load workspace")
        }
      })

    return () => {
      cancelled = true
    }
  }, [authenticated, request])

  useEffect(() => {
    if (!authenticated || !workspace) return

    let cancelled = false
    const loadExistingSources = async () => {
      try {
        const documents = await listDocumentsApi(request, workspace.knowledgeBase.id)
        if (cancelled) return
        setSources((current) => {
          const byId = new Map(current.map((source) => [source.id, source]))
          for (const document of documents) {
            byId.set(document.id, {
              ...sourceFromDocument(document),
              ...byId.get(document.id),
              status: document.status,
              chunkCount: document.chunkCount,
              errorMessage: document.errorMessage,
            })
          }
          return Array.from(byId.values()).sort((a, b) =>
            b.uploadedAt.localeCompare(a.uploadedAt),
          )
        })
      } catch (error) {
        toast.error(error instanceof Error ? error.message : "Unable to load documents")
      }
    }

    void loadExistingSources()

    return () => {
      cancelled = true
    }
  }, [authenticated, request, workspace])

  useEffect(() => {
    if (!authenticated || !workspace || !sessionId) return

    let cancelled = false
    getChatMessagesApi(request, sessionId)
      .then((history) => {
        if (cancelled || history.length === 0) return
        setMessages(
          history
            .filter((item) => item.role === "user" || item.role === "assistant")
            .map((item) => ({
              id: `${sessionId}-${item.sequence_number ?? makeId()}`,
              role: item.role as "user" | "assistant",
              content: item.content,
              citations: item.citations,
            })),
        )
      })
      .catch((error) => {
        if (cancelled) return
        if (error instanceof Error && error.message === "Chat session was not found.") {
          setSessionId(null)
          return
        }
        toast.error(error instanceof Error ? error.message : "Unable to load chat history")
      })

    return () => {
      cancelled = true
    }
  }, [authenticated, request, sessionId, workspace])

  useEffect(() => {
    return () => {
      activeChatAbortRef.current?.abort()
    }
  }, [])

  useEffect(() => {
    if (!authenticated || !workspace || !canPersistStateRef.current) return
    window.sessionStorage.setItem(
      playgroundStateKey(workspace),
      JSON.stringify({ sessionId, messages, sources } satisfies PersistedPlaygroundState),
    )
  }, [authenticated, messages, sessionId, sources, workspace])

  useEffect(() => {
    if (!authenticated) return
    const pollable = sources.filter(
      (source) =>
        source.status !== "UPLOADING" &&
        !isTerminalDocumentStatus(source.status),
    )
    if (pollable.length === 0) return

    let cancelled = false
    const poll = async () => {
      const updates = await Promise.allSettled(
        pollable.map((source) => getDocumentStatusApi(request, source.id)),
      )
      if (cancelled) return
      setSources((current) =>
        current.map((source) => {
          const index = pollable.findIndex((item) => item.id === source.id)
          if (index < 0) return source
          const result = updates[index]
          if (!result || result.status !== "fulfilled") return source
          return {
            ...source,
            status: result.value.status,
            chunkCount: result.value.chunkCount,
            errorMessage: result.value.errorMessage,
          }
        }),
      )
    }

    const timer = window.setInterval(() => {
      void poll()
    }, 1800)
    void poll()

    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [authenticated, request, sources])

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

  async function ensureSession(): Promise<string> {
    if (sessionId) return sessionId
    return createSession()
  }

  async function createSession(): Promise<string> {
    if (!workspace) {
      throw new Error("Workspace is still loading.")
    }
    const session = await createChatSessionApi(request, {
      chatbot_id: workspace.chatbot.id,
      knowledge_base_id: workspace.knowledgeBase.id,
      locale: workspace.chatbot.defaultLocale || workspace.knowledgeBase.defaultLocale,
    })
    setSessionId(session.id)
    return session.id
  }

  async function submitMessage(event?: FormEvent) {
    event?.preventDefault()
    if (requireAuthentication()) return
    const content = message.trim()
    if (!content || sending) return
    if (!hasCompletedSource) {
      toast.error(
        hasIndexingSource
          ? "Wait for at least one document to finish indexing."
          : "Upload a TXT or PDF source before asking a question.",
      )
      return
    }

    const assistantId = makeId()
    setMessages((current) => [
      ...current,
      { id: makeId(), role: "user", content },
      { id: assistantId, role: "assistant", content: "Thinking...", loading: true },
    ])
    setMessage("")
    setSending(true)

    const abortController = new AbortController()
    activeChatAbortRef.current = abortController

    try {
      const activeSessionId = await ensureSession()
      let response
      try {
        response = await submitChatMessageApi(
          request,
          activeSessionId,
          content,
          abortController.signal,
        )
      } catch (error) {
        if (error instanceof Error && error.message === "Chat session was not found.") {
          setSessionId(null)
          const replacementSessionId = await createSession()
          response = await submitChatMessageApi(
            request,
            replacementSessionId,
            content,
            abortController.signal,
          )
        } else {
          throw error
        }
      }
      setMessages((current) =>
        current.map((item) =>
          item.id === assistantId
            ? {
                id: assistantId,
                role: "assistant",
                content: response.content,
                citations: response.citations,
              }
            : item,
        ),
      )
    } catch (error) {
      const aborted = abortController.signal.aborted
      const errorMessage = aborted
        ? "Canceled."
        : error instanceof Error
          ? error.message
          : "Unable to answer."
      setMessages((current) =>
        current.map((item) =>
          item.id === assistantId
            ? {
                id: assistantId,
                role: "assistant",
                content: errorMessage,
                error: !aborted,
              }
            : item,
        ),
      )
    } finally {
      if (activeChatAbortRef.current === abortController) {
        activeChatAbortRef.current = null
      }
      setSending(false)
    }
  }

  function cancelMessage() {
    activeChatAbortRef.current?.abort()
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      void submitMessage()
    }
  }

  function chooseUpload() {
    if (requireAuthentication()) return
    setSourceMenuOpen(false)
    fileInputRef.current?.click()
  }

  async function uploadFiles(files: File[]) {
    if (!workspace) {
      toast.error("Workspace is still loading.")
      return
    }

    for (const file of files) {
      if (!isSupportedFile(file)) {
        toast.error(`${file.name} is not supported. Upload TXT or PDF files.`)
        continue
      }

      const localId = makeId()
      const pendingSource: SourceDocument = {
        id: localId,
        localId,
        fileName: file.name,
        fileType: fileTypeFromName(file.name),
        fileSizeBytes: file.size,
        jobId: localId,
        knowledgeBaseId: workspace.knowledgeBase.id,
        status: "UPLOADING",
        uploadedAt: new Date().toISOString(),
      }
      setSources((current) => [...current, pendingSource])

      try {
        const uploaded = await uploadDocumentApi(
          request,
          file,
          workspace.knowledgeBase.id,
        )
        setSources((current) =>
          current.map((source) =>
            source.localId === localId
              ? {
                  ...source,
                  id: uploaded.id,
                  jobId: uploaded.jobId,
                  fileName: uploaded.fileName,
                  status: uploaded.status,
                }
              : source,
          ),
        )
      } catch (error) {
        const errorMessage =
          error instanceof Error ? error.message : "Upload failed"
        setSources((current) =>
          current.map((source) =>
            source.localId === localId
              ? { ...source, status: "FAILED", errorMessage }
              : source,
          ),
        )
        toast.error(errorMessage)
      }
    }
  }

  function handleFiles(event: ChangeEvent<HTMLInputElement>) {
    const selectedFiles = Array.from(event.target.files ?? [])
    if (selectedFiles.length > 0) {
      void uploadFiles(selectedFiles)
    }
    event.target.value = ""
  }

  const sendDisabled =
    authenticated && (!workspace || !message.trim() || !hasCompletedSource || sending)

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
                Upload a TXT or text-based PDF, wait for indexing, then ask questions with citations.
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
                <div
                  key={item.id}
                  className={cn("flex", item.role === "user" ? "justify-end" : "justify-start")}
                >
                  <div
                    className={cn(
                      "max-w-[85%] whitespace-pre-wrap break-words rounded-2xl px-4 py-3 text-sm leading-6 sm:max-w-[75%]",
                      item.role === "user"
                        ? "rounded-br-md bg-indigo-600 text-white"
                        : item.error
                          ? "rounded-bl-md bg-red-50 text-red-700"
                          : "rounded-bl-md bg-slate-100 text-slate-900",
                    )}
                  >
                    {item.loading ? (
                      <span className="inline-flex items-center gap-2">
                        <Loader2 className="size-3.5 animate-spin" />
                        {item.content}
                      </span>
                    ) : (
                      item.content
                    )}
                    {item.citations && item.citations.length > 0 && (
                      <div className="mt-3 space-y-2 border-t border-slate-200 pt-3">
                        {item.citations.map((citation) => (
                          <div
                            key={`${citation.id}-${citation.document_id}-${citation.chunk_index}`}
                            className="rounded-lg border border-slate-200 bg-white p-2 text-xs text-slate-600"
                          >
                            <div className="mb-1 flex items-center justify-between gap-2">
                              <span className="font-semibold text-slate-800">
                                [{citation.id}] {citation.source_name}
                              </span>
                              <span>{citation.score.toFixed(2)}</span>
                            </div>
                            <p className="text-slate-500">
                              {citation.page_number ? `Page ${citation.page_number} · ` : ""}
                              Chunk {citation.chunk_index}
                            </p>
                            <p className="mt-1 line-clamp-3">{citation.snippet}</p>
                          </div>
                        ))}
                      </div>
                    )}
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
                  key={source.localId}
                  className="flex min-w-0 max-w-72 shrink-0 items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 py-2 pl-2.5 pr-1.5"
                  title={source.errorMessage ?? source.fileName}
                >
                  <div className="grid size-8 shrink-0 place-items-center rounded-md bg-white text-indigo-600 shadow-sm">
                    {source.status === "UPLOADING" || source.status === "PROCESSING" ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <FileText className="size-4" />
                    )}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-xs font-medium text-slate-800">
                      {source.fileName}
                    </p>
                    <p className="truncate text-[11px] text-slate-500">
                      {formatBytes(source.fileSizeBytes)}
                      {source.chunkCount ? ` · ${source.chunkCount} chunks` : ""}
                    </p>
                    <span
                      className={cn(
                        "mt-1 inline-flex rounded-full px-2 py-0.5 text-[10px] font-medium",
                        statusClass(source.status),
                      )}
                    >
                      {statusLabel(source.status)}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      setSources((current) =>
                        current.filter((item) => item.localId !== source.localId),
                      )
                    }
                    className="ml-1 rounded-md p-1 text-slate-400 hover:bg-slate-200 hover:text-slate-700"
                    aria-label={`Remove ${source.fileName}`}
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
              placeholder={
                hasCompletedSource
                  ? "Ask a question about your indexed sources..."
                  : "Upload and index a source before chatting..."
              }
              className="block max-h-36 min-h-14 w-full resize-none bg-transparent px-2 py-1.5 text-sm leading-6 text-slate-900 outline-none placeholder:text-slate-400"
              aria-label="Message"
            />
            <div className="flex h-9 items-center justify-between gap-2">
              <div className="relative">
                <button
                  type="button"
                  disabled={sending}
                  onClick={() => setSourceMenuOpen((open) => !open)}
                  className={cn(
                    "flex h-9 items-center gap-1.5 rounded-lg px-2.5 text-sm text-slate-600 hover:bg-slate-100 hover:text-slate-900",
                    sending && "cursor-not-allowed opacity-50",
                  )}
                  aria-expanded={sourceMenuOpen}
                >
                  <Paperclip className="size-4" />
                  <span>Add source</span>
                </button>
                {sourceMenuOpen && (
                  <div className="absolute bottom-11 left-0 z-20 w-44 rounded-xl border border-slate-200 bg-white p-1.5 shadow-xl">
                    <button
                      type="button"
                      onClick={chooseUpload}
                      className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100"
                    >
                      <Upload className="size-4" /> Upload TXT/PDF
                    </button>
                  </div>
                )}
                <input
                  ref={fileInputRef}
                  type="file"
                  multiple
                  accept=".txt,.pdf,text/plain,application/pdf"
                  className="hidden"
                  onChange={handleFiles}
                />
              </div>
              {sending && (
                <button
                  type="button"
                  onClick={cancelMessage}
                  className="h-9 rounded-lg px-3 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                >
                  Cancel
                </button>
              )}
              <button
                type={sending ? "button" : "submit"}
                disabled={!sending && sendDisabled}
                onClick={sending ? cancelMessage : undefined}
                className={cn(
                  "grid size-9 shrink-0 place-items-center rounded-lg text-white transition-colors",
                  sending
                    ? "bg-slate-900 hover:bg-slate-700"
                    : "bg-indigo-600 hover:bg-indigo-700",
                  !sending && sendDisabled &&
                    "cursor-not-allowed bg-slate-200 text-slate-400 hover:bg-slate-200",
                )}
                aria-label={sending ? "Cancel response" : "Send message"}
              >
                {sending ? <Square className="size-3.5 fill-current" /> : <ArrowUp className="size-4" />}
              </button>
            </div>
          </div>
          <p className="mt-2 text-center text-[11px] text-slate-400">
            Files are uploaded to CacaNode and indexed before answers are generated.
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
