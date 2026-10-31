interface StorageLike {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}
export function createSession(storage?: StorageLike) {
  let token: string | null = null
  try {
    token = storage?.getItem('ioj.token') ?? null
  } catch {
    /* Storage can be disabled. */
  }
  const listeners = new Set<(token: string | null) => void>()
  return {
    getToken: () => token,
    set(value: string | null) {
      token = value
      try {
        if (value) storage?.setItem('ioj.token', value)
        else storage?.removeItem('ioj.token')
      } catch {
        /* Keep the current tab usable. */
      }
      for (const notify of listeners) notify(token)
    },
    clearIfMatches(value: string | null) {
      if (value !== null && value === token) this.set(null)
    },
    subscribe(notify: (token: string | null) => void) {
      listeners.add(notify)
      return () => listeners.delete(notify)
    },
  }
}
let storage: StorageLike | undefined
try {
  storage = globalThis.sessionStorage
} catch {
  /* Private browsing may block storage. */
}
export const session = createSession(storage)
