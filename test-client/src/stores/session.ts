import { reactive } from 'vue'
import { getMe } from '../api/auth'
import { ApiError } from '../api/http'
import { clearToken, getToken, setToken } from '../api/token'
import type { LoginResponse, UserVO } from '../api/types'

export const session = reactive<{
  user: UserVO | null
  ready: boolean
}>({
  user: null,
  ready: false,
})

let pending: Promise<void> | null = null

export function applyLogin(result: LoginResponse): void {
  setToken(result.token)
  session.user = result.user
  session.ready = true
}

export function updateSessionUser(user: UserVO): void {
  session.user = user
}

export function logoutLocal(): void {
  clearToken()
  session.user = null
}

export function isAdmin(): boolean {
  return session.user?.role === 'ADMIN'
}

export function restoreSession(): Promise<void> {
  if (session.ready) return Promise.resolve()
  if (pending) return pending

  pending = (async () => {
    if (!getToken()) {
      session.ready = true
      return
    }
    try {
      session.user = await getMe()
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearToken()
        session.user = null
      }
    } finally {
      session.ready = true
    }
  })()

  return pending
}

if (typeof window !== 'undefined') {
  window.addEventListener('ioj:auth-expired', () => {
    session.user = null
  })
}
