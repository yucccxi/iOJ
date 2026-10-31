import { computed, onScopeDispose, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/api/resources'
import { session } from '@/api/session'
import type { User } from '@/types/api'
export const useAuthStore = defineStore('auth', () => {
  const current = ref<User | null>(null)
  const token = ref(session.getToken())
  const revision = ref(0)
  const ready = ref(false)
  let pending: Promise<void> | null = null
  const unsubscribe = session.subscribe((value) => {
    token.value = value
    revision.value++
    current.value = null
  })
  onScopeDispose(unsubscribe)
  async function verify() {
    if (pending) return pending
    const snapshot = session.getToken()
    if (!snapshot) {
      ready.value = true
      return
    }
    pending = (async () => {
      try {
        const user = await api.me()
        if (session.getToken() === snapshot) current.value = user
      } finally {
        ready.value = true
        pending = null
      }
    })()
    return pending
  }
  async function restore() {
    if (!ready.value) {
      try {
        await verify()
      } catch {
        /* The page shows its own service error. */
      }
    }
  }
  async function signIn(account: string, password: string, signal?: AbortSignal) {
    const result = await api.login({ account, password }, signal)
    session.set(result.token)
    current.value = result.user
    ready.value = true
  }
  function signOut() {
    session.set(null)
    current.value = null
  }
  function updateUser(user: User) {
    current.value = user
  }
  return {
    user: computed(() => current.value),
    token: computed(() => token.value),
    revision: computed(() => revision.value),
    isAdmin: computed(() => current.value?.role === 'ADMIN'),
    ready,
    restore,
    verify,
    signIn,
    signOut,
    updateUser,
  }
})
