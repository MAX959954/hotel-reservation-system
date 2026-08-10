import axios, { AxiosError } from 'axios'
import type { ApiError } from '@/types/auth'
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

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
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
