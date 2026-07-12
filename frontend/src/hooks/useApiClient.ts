'use client';

import { useCallback, useRef } from 'react'
import { useAuthStore } from '@/components/providers/StoreProvider'
import { getApiBase } from '@/lib/auth-api'

export function useApiClient() {
  const accessToken = useAuthStore(s => s.accessToken)
  const setAuth = useAuthStore(s => s.setAuth)
  const clearAuth = useAuthStore(s => s.clearAuth)

  const isRefreshingRef = useRef(false)
  const failedQueueRef = useRef<Array<{
    resolve: (token: string) => void
    reject: (err: unknown) => void
  }>>([])

  const processQueue = (error: unknown, token: string | null) => {
    failedQueueRef.current.forEach(p =>
      error ? p.reject(error) : p.resolve(token!)
    )
    failedQueueRef.current = []
  }

  const request = useCallback(async (
    endpoint: string,
    options: RequestInit = {}
  ): Promise<Response> => {

    const makeRequest = (token: string | null) => {
      const headers = new Headers(options.headers)
      if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json')
      }
      if (token) {
        headers.set('Authorization', `Bearer ${token}`)
      }

      return fetch(endpoint, {
        ...options,
        headers,
        credentials: 'include',
      })
    }

    let response = await makeRequest(accessToken)

    if (response.status !== 401) return response

    // If already refreshing — queue this request
    if (isRefreshingRef.current) {
      return new Promise<string>((resolve, reject) => {
        failedQueueRef.current.push({ resolve, reject })
      }).then(newToken => makeRequest(newToken))
    }

    isRefreshingRef.current = true

    try {
      const refreshResponse = await fetch(`${getApiBase()}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
      })

      if (!refreshResponse.ok) throw new Error('Refresh failed')

      const { accessToken: newToken, user } = await refreshResponse.json()
      setAuth(user, newToken, user.tenantId)
      processQueue(null, newToken)

      response = await makeRequest(newToken)
    } catch (err) {
      processQueue(err, null)
      clearAuth()
      window.location.href = '/login'
      throw new Error('Session expired')
    } finally {
      isRefreshingRef.current = false
    }

    return response
  }, [accessToken, setAuth, clearAuth])

  return { request }
}
