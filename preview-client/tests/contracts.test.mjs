import test from 'node:test'
import assert from 'node:assert/strict'
import { safeReturn, validateTestcases, problemInput, finished } from '../src/lib/contracts.ts'
import { createApiClient, ApiError } from '../src/api/client.ts'
import { createSession } from '../src/api/session.ts'

test('登录回跳只接受站内路径，防止外站和循环', () => {
  for (const input of [
    '//evil.example',
    'https://evil.example',
    '/\\evil.example',
    '/login?redirect=/login',
    null,
    '/\nother',
  ])
    assert.equal(safeReturn(input), '/problems')
  assert.equal(safeReturn('/problems/42?tab=submit'), '/problems/42?tab=submit')
})
test('测试数据按 UTF-8 字节限制，空输入有效，不接受缺失字段', () => {
  assert.deepEqual(validateTestcases([{ input: '', output: '' }]), [{ input: '', output: '' }])
  assert.throws(() => validateTestcases([{ input: '中'.repeat(350000), output: '' }]), /超过/)
  assert.throws(() => validateTestcases([{ input: '' }]), /input 和 output/)
  assert.throws(() => validateTestcases(Array(101).fill({ input: '', output: '' })), /最多/)
  assert.deepEqual(validateTestcases([]), [])
})
test('整体编辑保留样例、空字段和独立数组', () => {
  const value = {
    title: 'A+B',
    difficulty: 'EASY',
    description: 'a+b',
    timeLimitMs: 1000,
    memoryLimitMb: 256,
    tags: ['数学'],
    samples: [{ input: '1 2', output: '3' }],
  }
  const result = problemInput(value)
  assert.equal(result.hint, null)
  assert.equal(result.samples[0].explanation, null)
  result.tags.push('入门')
  assert.equal(value.tags.length, 1)
  assert.equal(finished('MEMORY_LIMIT_EXCEEDED'), true)
  assert.equal(finished('JUDGING'), false)
})
test('请求保留零值、编码筛选条件、解析统一响应及认证头', async () => {
  let captured
  const request = createApiClient({
    getToken: () => 'test-token',
    fetchImpl: async (url, init) => {
      captured = { url, init }
      return Response.json({ code: 0, data: { items: [], total: 0 } })
    },
  })
  assert.deepEqual(
    await request('/problems', {
      query: { keyword: 'a+b 中文', page: 0, empty: '', absent: null },
    }),
    { items: [], total: 0 },
  )
  assert.equal(captured.init.headers.Authorization, 'Bearer test-token')
  assert.equal(captured.url, '/api/v1/problems?keyword=a%2Bb+%E4%B8%AD%E6%96%87&page=0')
})
test('401 通知携带发起请求时的令牌，业务登录错误不误清会话', async () => {
  let token = 'old',
    notified
  const request = createApiClient({
    getToken: () => token,
    onUnauthorized: (value) => {
      notified = value
    },
    fetchImpl: async () => {
      token = 'new'
      return Response.json({ code: 1002, message: '未登录', data: null }, { status: 401 })
    },
  })
  await assert.rejects(
    () => request('/auth/me'),
    (error) => error instanceof ApiError && error.code === 1002,
  )
  assert.equal(notified, 'old')
  notified = undefined
  await assert.rejects(
    createApiClient({
      onUnauthorized: () => {
        notified = true
      },
      fetchImpl: async () =>
        Response.json({ code: 2005, message: '账号或密码错误' }, { status: 401 }),
    })('/auth/login'),
  )
  assert.equal(notified, undefined)
})
test('取消请求保留取消信号，网络错误和非 JSON 响应可读', async () => {
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(
    () =>
      createApiClient({
        fetchImpl: async () => {
          throw new DOMException('cancel', 'AbortError')
        },
      })('/x', { signal: controller.signal }),
    { name: 'AbortError' },
  )
  await assert.rejects(
    createApiClient({
      fetchImpl: async () => {
        throw new TypeError('network')
      },
    })('/x'),
    /无法连接/,
  )
  await assert.rejects(
    createApiClient({ fetchImpl: async () => new Response('<html>', { status: 502 }) })('/x'),
    /HTTP 502/,
  )
})

test('旧会话请求不能清理新令牌；存储不可用时仍能登录退出', () => {
  const session = createSession({
    getItem() {
      throw new Error('blocked')
    },
    setItem() {
      throw new Error('blocked')
    },
    removeItem() {
      throw new Error('blocked')
    },
  })
  const events = []
  const unsubscribe = session.subscribe((token) => events.push(token))
  session.set('old')
  session.set('new')
  session.clearIfMatches('old')
  assert.equal(session.getToken(), 'new')
  session.clearIfMatches('new')
  assert.equal(session.getToken(), null)
  unsubscribe()
  session.set('last')
  assert.deepEqual(events, ['old', 'new', null])
})
