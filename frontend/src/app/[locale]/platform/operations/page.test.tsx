import { act, fireEvent, render, screen } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import PlatformOperationsPage from "./page"

const request = vi.hoisted(() => vi.fn())

vi.mock("@/hooks/useApiClient", () => ({ useApiClient: () => ({ request }) }))
vi.mock("@/lib/auth-api", () => ({ getApiBase: () => "http://api.test/api/v1" }))
vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
  useFormatter: () => ({
    number: (value: number) => String(value),
    dateTime: () => "Jul 28, 2026",
  }),
}))

const health = {
  snapshotTime: "2026-07-28T00:00:00Z", overallStatus: "DEGRADED",
  components: [{ component: "BUSINESS_API_JVM", status: "UP", latencyMilliseconds: 1, checkedAt: "2026-07-28T00:00:00Z", errorCode: null }],
  runtimeMetrics: { scope: "APPLICATION_CONTAINER", cpuScope: "JVM_PROCESS", processCpuPercentage: 2.5, availableProcessors: 4, heapUsedBytes: 1024, heapCommittedBytes: 2048, heapMaxBytes: 4096, jvmUptimeMilliseconds: 60_000, filesystemTotalBytes: 8192, filesystemUsableBytes: 4096 },
}
const queues = {
  items: [{ queueId: "DOCUMENT_INGESTION", domain: "DOCUMENT", deadLetterQueue: false, readyCount: 25, consumerCount: 0, status: "DEGRADED", checkedAt: "2026-07-28T00:00:00Z", errorCode: "CONSUMERS_ABSENT" }],
  page: 0, size: 20, total: 1, snapshotTime: "2026-07-28T00:00:00Z", overallStatus: "DEGRADED", warningDepth: 25, criticalDepth: 100,
}

describe("platform operations page", () => {
  beforeEach(() => {
    request.mockReset().mockImplementation(async (endpoint: string) => ({
      ok: true,
      json: async () => endpoint.includes("/health") ? health : queues,
    } as Response))
    Object.defineProperty(document, "visibilityState", { configurable: true, value: "visible" })
  })
  afterEach(() => { vi.useRealTimers() })

  it("renders component, scoped runtime, queue, and controlled error states", async () => {
    render(<PlatformOperationsPage />)
    expect(await screen.findByText("componentNames.BUSINESS_API_JVM")).toBeInTheDocument()
    expect(screen.getAllByText("queueNames.DOCUMENT_INGESTION").length).toBeGreaterThan(0)
    expect(screen.getAllByText("scopes.JVM_PROCESS").length).toBeGreaterThan(0)
    expect(screen.getAllByText("errors.CONSUMERS_ABSENT").length).toBeGreaterThan(0)
    expect(request).toHaveBeenCalledTimes(2)
  })

  it("polls every fifteen seconds, stops hidden, resumes immediately, and refreshes manually", async () => {
    vi.useFakeTimers()
    render(<PlatformOperationsPage />)
    await flush()
    expect(request).toHaveBeenCalledTimes(2)
    await act(async () => { await vi.advanceTimersByTimeAsync(15_000) })
    expect(request).toHaveBeenCalledTimes(4)

    Object.defineProperty(document, "visibilityState", { configurable: true, value: "hidden" })
    act(() => document.dispatchEvent(new Event("visibilitychange")))
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000) })
    expect(request).toHaveBeenCalledTimes(4)

    Object.defineProperty(document, "visibilityState", { configurable: true, value: "visible" })
    act(() => document.dispatchEvent(new Event("visibilitychange")))
    await flush()
    expect(request).toHaveBeenCalledTimes(6)

    fireEvent.click(screen.getByRole("button", { name: "refresh" }))
    await flush()
    expect(request).toHaveBeenCalledTimes(8)
  })
})

async function flush() {
  await act(async () => { await Promise.resolve(); await Promise.resolve() })
}
