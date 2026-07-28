import { getApiBase } from "@/lib/auth-api"
import { parseApiError, readJsonOrThrow } from "@/lib/api-error"
import type { ApiRequest } from "@/lib/api-request"

export type PlatformStaffStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED"
export type PlatformInvitationStatus = "PENDING" | "EXPIRED" | "CANCELLED" | "ACCEPTED"

export interface PlatformPage<T> { items: T[]; page: number; size: number; total: number }
export interface PlatformStaffItem {
  id: string; email: string; name: string; role: "PLATFORM_ADMIN"; status: PlatformStaffStatus
  createdAt: string; lastLoginAt: string | null; currentUser: boolean
}
export interface PlatformInvitationItem {
  id: string; email: string; role: "PLATFORM_ADMIN"; status: PlatformInvitationStatus
  createdAt: string; expiresAt: string; lastSentAt: string
}
export interface PlatformListQuery {
  page: number; size: number; q?: string; status?: string; sort?: string; direction?: "asc" | "desc"
}

function queryString(query: PlatformListQuery) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value))
  })
  return params.toString()
}

export async function listPlatformStaff(request: ApiRequest, query: PlatformListQuery) {
  return readJsonOrThrow<PlatformPage<PlatformStaffItem>>(
    await request(`${getApiBase()}/platform/staff?${queryString(query)}`),
  )
}

export async function listPlatformInvitations(request: ApiRequest, query: PlatformListQuery) {
  return readJsonOrThrow<PlatformPage<PlatformInvitationItem>>(
    await request(`${getApiBase()}/platform/staff/invitations?${queryString(query)}`),
  )
}

export async function invitePlatformStaff(request: ApiRequest, email: string) {
  return readJsonOrThrow<PlatformInvitationItem>(await request(`${getApiBase()}/platform/staff/invitations`, {
    method: "POST", body: JSON.stringify({ email }),
  }))
}

export async function resendPlatformInvitation(request: ApiRequest, id: string) {
  return readJsonOrThrow<PlatformInvitationItem>(await request(`${getApiBase()}/platform/staff/invitations/${id}/resend`, { method: "POST" }))
}

export async function cancelPlatformInvitation(request: ApiRequest, id: string) {
  const response = await request(`${getApiBase()}/platform/staff/invitations/${id}`, { method: "DELETE" })
  if (!response.ok) throw await parseApiError(response)
}

export async function updatePlatformStaffStatus(request: ApiRequest, id: string, status: "ACTIVE" | "INACTIVE") {
  return readJsonOrThrow<PlatformStaffItem>(await request(`${getApiBase()}/platform/staff/${id}/status`, {
    method: "PATCH", body: JSON.stringify({ status }),
  }))
}
