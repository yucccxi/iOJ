import { watch } from 'vue'
import { defineStore } from 'pinia'
import type { Language } from '@/types/api'
import { useAuthStore } from './auth'

interface CodeDraft {
  code: string
  language: Language | ''
}

export const useCodeDraftsStore = defineStore('code-drafts', () => {
  const auth = useAuthStore()
  const drafts = new Map<string, CodeDraft>()

  // Guest drafts survive login; replacing or ending an authenticated session clears them.
  watch(
    () => auth.token,
    (_token, previousToken) => {
      if (previousToken) drafts.clear()
    },
    { flush: 'sync' },
  )

  function read(problemId: string): CodeDraft {
    return { ...(drafts.get(problemId) || { code: '', language: '' }) }
  }

  function save(problemId: string, draft: CodeDraft) {
    if (!draft.code && !draft.language) drafts.delete(problemId)
    else drafts.set(problemId, { ...draft })
  }

  return { read, save }
})
