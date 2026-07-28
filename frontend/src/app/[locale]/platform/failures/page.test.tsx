import { act, fireEvent, render, screen } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import PlatformFailuresPage from "./page"

const request = vi.hoisted(() => vi.fn())
const replace = vi.hoisted(() => vi.fn())

vi.mock("@/hooks/useApiClient", () => ({ useApiClient: () => ({ request }) }))
vi.mock("@/lib/auth-api", () => ({ getApiBase: () => "http://api.test/api/v1" }))
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams() }))
vi.mock("@/i18n/navigation", () => ({
  useRouter: () => ({ replace }),
  Link: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) => <a href={href} {...props}>{children}</a>,
}))
vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
  useFormatter: () => ({ number: (value: number) => String(value), dateTime: () => "Jul 28, 2026" }),
}))

const summary = {
  generatedAt: "2026-07-28T00:00:00Z", partial: false, warnings: [],
  sources: [{ source: "MODULE_EVENTS", total: 1, states: { RETRYING: 1 }, severities: { WARNING: 1 } }],
}
const page = {
  generatedAt: "2026-07-28T00:00:00Z", source: "MODULE_EVENTS", page: 0, size: 20, total: 1, partial: false, warnings: [],
  items: [{ source: "MODULE_EVENTS", failureId: "00000000-0000-4000-8000-000000000001", tenantId: "00000000-0000-4000-8000-000000000002", resourceId: null, resourceType: "MODULE_EVENT", state: "RETRYING", severity: "WARNING", errorCode: "MODULE_EVENT_RETRY", attempts: 2, firstSeenAt: "2026-07-28T00:00:00Z", lastSeenAt: "2026-07-28T00:01:00Z", nextRetryAt: "2026-07-28T00:02:00Z" }],
}

describe("platform failure explorer", () => {
  beforeEach(() => {
    request.mockReset().mockImplementation(async (endpoint: string) => ({ ok: true, json: async () => endpoint.includes("/summary") ? summary : page } as Response))
    replace.mockReset()
    Object.defineProperty(document, "visibilityState", { configurable: true, value: "visible" })
  })
  afterEach(() => vi.useRealTimers())

  it("renders localized controlled metadata and responsive result views", async () => {
    render(<PlatformFailuresPage />)
    expect(await screen.findAllByText("codes.MODULE_EVENT_RETRY")).toHaveLength(2)
    expect(screen.getAllByText("states.RETRYING").length).toBeGreaterThan(0)
    expect(screen.getAllByText("resourceTypes.MODULE_EVENT")).toHaveLength(2)
    expect(screen.getAllByRole("link", { name: "00000000-0000-4000-8000-000000000002" })[0]).toHaveAttribute("href", "/platform/tenants/00000000-0000-4000-8000-000000000002")
  })

  it("polls only summary while visible and manual refresh updates both", async () => {
    vi.useFakeTimers()
    render(<PlatformFailuresPage />)
    await flush()
    expect(request).toHaveBeenCalledTimes(2)
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000) })
    expect(request).toHaveBeenCalledTimes(3)

    Object.defineProperty(document, "visibilityState", { configurable: true, value: "hidden" })
    act(() => document.dispatchEvent(new Event("visibilitychange")))
    await act(async () => { await vi.advanceTimersByTimeAsync(60_000) })
    expect(request).toHaveBeenCalledTimes(3)

    Object.defineProperty(document, "visibilityState", { configurable: true, value: "visible" })
    act(() => document.dispatchEvent(new Event("visibilitychange")))
    await flush()
    expect(request).toHaveBeenCalledTimes(4)

    fireEvent.click(screen.getByRole("button", { name: "refreshAria" }))
    await flush()
    expect(request).toHaveBeenCalledTimes(6)
  })
})

async function flush() {
  await act(async () => { await vi.advanceTimersByTimeAsync(0); await Promise.resolve(); await Promise.resolve() })
}
