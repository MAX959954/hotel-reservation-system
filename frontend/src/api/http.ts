import axios, { AxiosError } from 'axios'
import type { ApiError } from '@/types/auth'
import { useAuthStore } from '@/stores/auth'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `${auth.tokenType} ${auth.token}`
  }
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
