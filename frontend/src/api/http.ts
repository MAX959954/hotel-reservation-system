import axios, { AxiosError } from 'axios'
import type { AxiosRequestConfig } from 'axios'
import type { ApiError, AuthResponse } from '@/types/auth'
import { useAuthStore } from '@/stores/auth'
import { i18n } from '@/i18n'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export const http = axios.create({
  baseURL: API_BASE_URL,
})

/** Uploaded assets (avatars, ...) come back from the API as server-relative paths
 *  like `/uploads/avatars/xxx.png` — this makes them loadable from an `<img>`. */
export function resolveUploadUrl(path: string | null | undefined): string | null {
  if (!path) return null
  return `${API_BASE_URL}${path}`
}

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `${auth.tokenType} ${auth.token}`
  }
  // Drives which hotel_translations row the backend serves for hotel/apartment
  // descriptions (see HotelController) — every request carries it, not just the hotel
  // endpoints, since it costs nothing extra and keeps this in one place.
  config.headers['Accept-Language'] = i18n.global.locale.value
  return config
})

type RetryableConfig = AxiosRequestConfig & { _retriedAfterRefresh?: boolean }

// jwt.expiration is short on purpose (~15 min) now that refresh tokens exist — this is
// what makes that not mean re-entering a password every 15 minutes. Calls http directly
// (not authApi.refresh) to avoid a module cycle: api/auth.ts itself imports http from here.
let refreshInFlight: Promise<string | null> | null = null

function refreshAccessToken(): Promise<string | null> {
  const auth = useAuthStore()
  if (!auth.refreshToken) return Promise.resolve(null)

  // Concurrent 401s (several requests in flight when the access token expires) must
  // share one refresh call — each racing off to consume the same refresh token would
  // mean only the first succeeds and the rest get treated as a leaked/replayed token.
  if (!refreshInFlight) {
    refreshInFlight = http
      .post<AuthResponse>('/api/auth/refresh', { refreshToken: auth.refreshToken })
      .then(({ data }) => {
        auth.setTokens(data.token, data.refreshToken)
        return data.token
      })
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetryableConfig | undefined
    const isAuthEndpoint = original?.url?.includes('/api/auth/')

    if (error.response?.status === 401 && original && !original._retriedAfterRefresh && !isAuthEndpoint) {
      original._retriedAfterRefresh = true
      const newToken = await refreshAccessToken()
      if (newToken) {
        original.headers = { ...original.headers, Authorization: `Bearer ${newToken}` }
        return http(original)
      }
    }

    if (error.response?.status === 401) {
      useAuthStore().logout()
    }
    return Promise.reject(error)
  },
)

/**
 * Pulls the server's own wording out of an `ApiError` body. The backend puts the
 * useful sentence in `message` (and field-level detail in `errors`), so showing a
 * generic "Something went wrong" would be throwing away the only text that tells
 * the user what to actually do.
 */
export function apiErrorMessage(error: unknown, fallback = 'Something went wrong. Try again.'): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiError | undefined
    if (body?.errors?.length) return body.errors.join(' ')
    if (body?.message) return body.message
    if (!error.response) return 'Cannot reach the server. Is the backend running?'
  }
  return fallback
}
