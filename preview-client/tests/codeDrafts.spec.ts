import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { useCodeDraftsStore } from '@/stores/codeDrafts'
import { session } from '@/api/session'

let pinia: ReturnType<typeof createPinia>
function reload() {
  disposePinia(pinia)
  pinia = createPinia()
  setActivePinia(pinia)
  return useCodeDraftsStore()
}
beforeEach(() => {
  sessionStorage.clear()
  session.set(null)
  pinia = createPinia()
  setActivePinia(pinia)
})
afterEach(() => {
  disposePinia(pinia)
  vi.restoreAllMocks()
  sessionStorage.clear()
  session.set(null)
})

test('刷新重建状态后按题目恢复完整代码和语言，清空也持久保存', () => {
  const drafts = useCodeDraftsStore()
  drafts.save('13', { code: 'int main() {\n  return 13;\n}', language: 'CPP' })
  drafts.save('14', { code: 'print(14)', language: 'PYTHON3' })
  const restored = reload()
  expect(restored.read('13')).toEqual({ code: 'int main() {\n  return 13;\n}', language: 'CPP' })
  expect(restored.read('14')).toEqual({ code: 'print(14)', language: 'PYTHON3' })
  restored.save('13', { code: '', language: '' })
  expect(reload().read('13')).toEqual({ code: '', language: '' })
})

test('访客登录后草稿保留，退出及替换认证后刷新不能恢复旧草稿', () => {
  const drafts = useCodeDraftsStore()
  drafts.save('13', { code: 'guest', language: 'C' })
  session.set('first-session')
  expect(reload().read('13').code).toBe('guest')
  session.set('second-session')
  expect(reload().read('13').code).toBe('')
  useCodeDraftsStore().save('13', { code: 'private', language: 'C' })
  session.set(null)
  expect(reload().read('13').code).toBe('')
})

test('存储禁用时编辑和内存读取仍可用', () => {
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
    throw new Error('blocked')
  })
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
    throw new Error('quota')
  })
  const drafts = useCodeDraftsStore()
  expect(() => drafts.save('13', { code: 'still editable', language: 'C' })).not.toThrow()
  expect(drafts.read('13').code).toBe('still editable')
})

test('损坏或不合法的持久草稿不阻塞页面', () => {
  sessionStorage.setItem('ioj.code-drafts.v1', '{broken')
  expect(useCodeDraftsStore().read('13').code).toBe('')
  sessionStorage.setItem(
    'ioj.code-drafts.v1',
    JSON.stringify([['13', { code: 123, language: 'C' }]]),
  )
  expect(reload().read('13').code).toBe('')
})
