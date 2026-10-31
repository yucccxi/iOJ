import { watch } from 'vue'
import { defineStore } from 'pinia'
import type { Language } from '@/types/api'
import { useAuthStore } from './auth'

interface CodeDraft {
  code: string
  language: Language | ''
}
const storageKey = 'ioj.code-drafts.v1'
const languages = new Set(['', 'JAVA', 'CPP', 'C', 'PYTHON3'])

export const useCodeDraftsStore = defineStore('code-drafts', () => {
  const auth = useAuthStore()
  const drafts = new Map<string, CodeDraft>()

  try {
    const saved: unknown = JSON.parse(sessionStorage.getItem(storageKey) || '[]')
    if (Array.isArray(saved)) {
      for (const entry of saved) {
        if (!Array.isArray(entry) || entry.length !== 2) continue
        const [id, draft] = entry
        if (
          typeof id === 'string' &&
          draft &&
          typeof draft.code === 'string' &&
          draft.code.length <= 65536 &&
          languages.has(draft.language)
        )
          drafts.set(id, { code: draft.code, language: draft.language })
      }
    }
  } catch {
    /* Invalid or unavailable storage must not prevent editing. */
  }

  function persist() {
    try {
      if (drafts.size) sessionStorage.setItem(storageKey, JSON.stringify([...drafts]))
      else sessionStorage.removeItem(storageKey)
    } catch {
      /* Keep in-memory drafts usable when storage is blocked or full. */
    }
  }

  // Guest drafts survive login; replacing or ending an authenticated session clears them.
  watch(
    () => auth.token,
    (_token, previousToken) => {
      if (previousToken) {
        drafts.clear()
        persist()
      }
    },
    { flush: 'sync' },
  )

  function read(problemId: string): CodeDraft {
    return { ...(drafts.get(problemId) || { code: '', language: '' }) }
  }

  function save(problemId: string, draft: CodeDraft) {
    if (!draft.code && !draft.language) drafts.delete(problemId)
    else drafts.set(problemId, { ...draft })
    persist()
  }

  return { read, save }
})
