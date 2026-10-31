import { beforeEach, afterEach, describe, test, expect, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { effectScope } from 'vue'
import { EditorView } from '@codemirror/view'
import App from '@/App.vue'
import router from '@/router'
import { session } from '@/api/session'
import { useAuthStore } from '@/stores/auth'
import { useResource } from '@/composables/useResource'
import { setHandler } from './setup'

const alice = {
  id: 1,
  username: 'alice',
  nickname: 'Alice',
  role: 'USER',
  createdAt: '2026-10-30T10:00:00',
}
const admin = { ...alice, id: 2, username: 'admin', nickname: '管理员', role: 'ADMIN' }
const problem = {
  id: 42,
  title: 'A+B Problem',
  difficulty: 'EASY',
  description: '计算 **a+b**。',
  inputFormat: '两个整数',
  outputFormat: '一个整数',
  hint: '使用整数',
  source: '俱乐部',
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  authorId: 1,
  authorNickname: 'Alice',
  status: 'PUBLISHED',
  submitCount: 3,
  acceptedCount: 2,
  tags: [{ id: 1, name: '数学' }],
  samples: [{ ordinal: 1, input: '1 2\n', output: '3\n', explanation: '1+2=3' }],
  solved: false,
  createdAt: '2026-10-30T10:00:00',
  updatedAt: '2026-10-30T10:00:00',
}
let wrapper: VueWrapper
let pinia: ReturnType<typeof createPinia>
let requests: { path: string; method: string; body: unknown }[]
let testcases: { id: number; ordinal: number; inputBytes: number; outputBytes: number }[]
let submissions: number
let failProblems: boolean
let description: string
let listTotal: number
function codeView() {
  const view = EditorView.findFromDOM(wrapper.get('.cm-editor').element as HTMLElement)
  if (!view) throw new Error('Code editor is not mounted')
  return view
}
function codeText() {
  return codeView().state.doc.toString()
}
async function setCode(value: string) {
  const view = codeView()
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } })
  await flushPromises()
}
function json(data: unknown, code = 0, status = 200, message = 'ok') {
  return Response.json({ code, message, data }, { status })
}
const mockHttp: typeof fetch = async (input, init = {}) => {
  const url = new URL(String(input), 'http://localhost'),
    path = url.pathname.replace('/api/v1', '')
  const method = init.method || 'GET'
  const body = typeof init.body === 'string' ? JSON.parse(init.body) : undefined
  requests.push({ path, method, body })
  const token = (init.headers as Record<string, string> | undefined)?.Authorization
  const user =
    token === 'Bearer admin-token' ? admin : token === 'Bearer alice-token' ? alice : null
  if (path === '/auth/login')
    return body.password === 'password123'
      ? json({
          token: body.account === 'admin' ? 'admin-token' : 'alice-token',
          user: body.account === 'admin' ? admin : alice,
          tokenType: 'Bearer',
          expiresIn: 604800,
        })
      : json(null, 2005, 401, '用户名或密码错误')
  if (path === '/auth/register') return json(alice)
  if (path === '/auth/me') return user ? json(user) : json(null, 1002, 401, '未登录')
  if (path === '/meta/tags') return json(problem.tags)
  if (path === '/meta/languages')
    return json([
      { code: 'JAVA', displayName: 'Java 21' },
      { code: 'CPP', displayName: 'C++17' },
    ])
  if (path === '/problems/42/testcases') {
    if (!user) return json(null, 1002, 401, '未登录')
    if (method === 'POST') {
      testcases.push({ id: 5, ordinal: 1, inputBytes: 4, outputBytes: 2 })
      return json(testcases[0])
    }
    if (method === 'PUT') {
      testcases = body.map((_: unknown, i: number) => ({
        id: i + 5,
        ordinal: i + 1,
        inputBytes: 4,
        outputBytes: 2,
      }))
      return json(testcases)
    }
    return json(testcases)
  }
  if (path.startsWith('/problems/42/testcases/')) {
    if (method === 'DELETE') {
      testcases = []
      return json(null)
    }
    return json({ id: 5, ordinal: 1, input: '1 2\n', output: '3\n' })
  }
  if (path === '/problems' && method === 'POST') return json({ ...problem, ...body })
  if (path === '/problems/42')
    return method === 'DELETE'
      ? json(null)
      : json({ ...problem, ...(method === 'PUT' ? body : {}), description })
  if (path === '/problems/43') return json({ ...problem, id: 43, title: '另一道题目' })
  if (path === '/problems' || path === '/problems/mine' || path === '/admin/problems') {
    if (failProblems) return json(null, 1500, 500, '服务器内部错误')
    return json({
      items: [problem],
      total: listTotal,
      page: Number(url.searchParams.get('page')) || 1,
      size: 20,
    })
  }
  if (path === '/submissions' && method === 'POST') {
    submissions = 0
    return json({ id: 99, status: 'PENDING' })
  }
  if (path === '/submissions')
    return json({
      items: [],
      total: listTotal,
      page: Number(url.searchParams.get('page')) || 1,
      size: 20,
    })
  if (path === '/submissions/99') {
    submissions++
    return json({
      id: 99,
      problemId: 42,
      problemTitle: problem.title,
      userId: 1,
      userNickname: 'Alice',
      language: 'JAVA',
      status: submissions < 2 ? 'JUDGING' : 'ACCEPTED',
      code: user ? 'public class Main {}' : null,
      compileMessage: null,
      judgeMessage: null,
      timeUsedMs: 10,
      memoryUsedKb: null,
      createdAt: problem.createdAt,
      judgedAt: null,
      cases:
        user && submissions > 1
          ? [{ ordinal: 1, status: 'ACCEPTED', timeUsedMs: 10, memoryUsedKb: null, message: null }]
          : [],
    })
  }
  if (path.startsWith('/users/')) return json({ ...alice, solvedCount: 3 })
  if (path === '/admin/users')
    return json({
      items: [admin, alice],
      total: listTotal,
      page: Number(url.searchParams.get('page')) || 1,
      size: 20,
    })
  if (path.startsWith('/admin/'))
    return user?.role === 'ADMIN' ? json(null) : json(null, 1003, 403, '没有权限')
  throw new Error('Unhandled test endpoint: ' + method + ' ' + path)
}
async function go(path: string) {
  await router.push(path)
  await vi.dynamicImportSettled()
  await flushPromises()
}
async function login(account = 'alice') {
  await go('/login')
  await wrapper.get('input[autocomplete=username]').setValue(account)
  await wrapper.get('input[type=password]').setValue('password123')
  await wrapper.get('form').trigger('submit')
  await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/problems'))
  await vi.dynamicImportSettled()
  await flushPromises()
}
beforeEach(async () => {
  session.set(null)
  requests = []
  testcases = []
  submissions = 0
  failProblems = false
  description = problem.description
  listTotal = 1
  setHandler(mockHttp)
  pinia = createPinia()
  setActivePinia(pinia)
  await useAuthStore().restore()
  await go('/problems')
  wrapper = mount(App, { attachTo: document.body, global: { plugins: [pinia, router] } })
  await vi.dynamicImportSettled()
  await flushPromises()
})
afterEach(() => {
  wrapper?.unmount()
  disposePinia(pinia)
  session.set(null)
  document.body.innerHTML = ''
})

describe('基于接口契约的模拟 DOM 交互（不替代真实浏览器验收）', () => {
  test('公告位于导航上方，切换作答页和关闭公告不重建页面', async () => {
    const notice = wrapper.get('[aria-label="公告提示"]')
    expect(notice.element.nextElementSibling).toBe(wrapper.get('header').element)
    await go('/problems/42')
    await setCode('int main() { return 42; }')
    const editor = wrapper.get('.cm-editor').element
    await wrapper.get('button[aria-label="关闭公告提示"]').trigger('click')
    expect(wrapper.find('[aria-label="公告提示"]').exists()).toBe(false)
    expect(wrapper.get('.cm-editor').element).toBe(editor)
    expect(codeText()).toBe('int main() { return 42; }')
    window.sessionStorage.removeItem('ioj:announcement:3588dc9a:dismissed')
  })
  test('实际 Transition 启用时，退出立即清除私密内容', async () => {
    await login()
    await go('/submissions/99')
    wrapper.unmount()
    wrapper = mount(App, {
      attachTo: document.body,
      global: { plugins: [pinia, router], stubs: { transition: false } },
    })
    await vi.waitFor(() => expect(wrapper.find('.source-code').exists()).toBe(true))
    await wrapper
      .findAll('header button')
      .find((button) => button.text() === '退出')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.find('.source-code').exists()).toBe(false)
    await vi.waitFor(() => expect(wrapper.text()).toContain('源码未公开'))
  })
  test('未登录作答后登录返回，保留代码与语言', async () => {
    await go('/problems/42')
    await wrapper.get('.editor-toolbar select').setValue('JAVA')
    await setCode('public class Main { /* 草稿 */ }')
    await wrapper.get('.editor-footer a').trigger('click')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/login'))
    await vi.dynamicImportSettled()
    await flushPromises()
    await wrapper.get('input[autocomplete=username]').setValue('alice')
    await wrapper.get('input[type=password]').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/problems/42'))
    await vi.dynamicImportSettled()
    await flushPromises()
    expect(codeText()).toBe('public class Main { /* 草稿 */ }')
    expect((wrapper.get('.editor-toolbar select').element as HTMLSelectElement).value).toBe('JAVA')
  })
  test('不同题目的草稿独立，离开后返回恢复各自内容', async () => {
    await go('/problems/42')
    await wrapper.get('.editor-toolbar select').setValue('CPP')
    await setCode('int main() { return 42; }')
    await go('/problems/43')
    expect(codeText()).toBe('')
    await wrapper.get('.editor-toolbar select').setValue('JAVA')
    await setCode('public class Main { /* 43 */ }')
    await go('/problems/42')
    expect(codeText()).toBe('int main() { return 42; }')
    expect((wrapper.get('.editor-toolbar select').element as HTMLSelectElement).value).toBe('CPP')
    await go('/problems/43')
    expect(codeText()).toBe('public class Main { /* 43 */ }')
  })
  test.each(['退出', '会话失效'])('%s 后清空作答草稿，其他账号不能恢复', async (mode) => {
    await login()
    await go('/problems/42')
    await wrapper.get('.editor-toolbar select').setValue('JAVA')
    await setCode('private code from alice')
    if (mode === '退出')
      await wrapper
        .findAll('header button')
        .find((button) => button.text() === '退出')!
        .trigger('click')
    else session.set(null)
    await vi.dynamicImportSettled()
    await flushPromises()
    expect(codeText()).toBe('')
    await login('admin')
    await go('/problems/42')
    expect(codeText()).toBe('')
    expect((wrapper.get('.editor-toolbar select').element as HTMLSelectElement).value).toBe('')
  })
  test.each([
    {
      url: '/problems?keyword=old&difficulty=EASY&tagId=1',
      account: '',
      edits: { 'input[type=search]': 'new', select: 'HARD' },
      expected: { keyword: 'new', difficulty: 'HARD', tagId: '1' },
    },
    {
      url: '/problems/mine?keyword=old',
      account: 'alice',
      edits: { 'input[type=search]': 'new' },
      expected: { keyword: 'new' },
    },
    {
      url: '/submissions?problemId=42&userId=1&status=WRONG_ANSWER',
      account: '',
      edits: { 'input[type=number]': '43', 'label:nth-child(2) input': '2', select: 'ACCEPTED' },
      expected: { problemId: '43', userId: '2', status: 'ACCEPTED' },
    },
    {
      url: '/admin/problems?keyword=old&status=PENDING_REVIEW',
      account: 'admin',
      edits: { 'input[type=search]': 'new', select: 'HIDDEN' },
      expected: { keyword: 'new', status: 'HIDDEN' },
    },
    {
      url: '/admin/users?keyword=alice',
      account: 'admin',
      edits: { 'input[type=search]': 'bob' },
      expected: { keyword: 'bob' },
    },
  ])(
    '$url：分页保留已应用条件，查询应用输入并回到首页',
    async ({ url, account, edits, expected }) => {
      listTotal = 45
      if (account) await login(account)
      await go(url)
      const applied = Object.fromEntries(new URL(url, 'http://localhost').searchParams)
      for (const [selector, value] of Object.entries(edits))
        await wrapper.get('.filter-bar ' + selector).setValue(value)
      await wrapper
        .findAll('.pagination button')
        .find((button) => button.text() === '下一页')!
        .trigger('click')
      await vi.waitFor(() => expect(router.currentRoute.value.query.page).toBe('2'))
      expect(router.currentRoute.value.query).toEqual({ ...applied, page: '2' })
      await vi.dynamicImportSettled()
      await flushPromises()
      for (const [selector, value] of Object.entries(edits))
        await wrapper.get('.filter-bar ' + selector).setValue(value)
      await wrapper.get('form.filter-bar').trigger('submit')
      await vi.waitFor(() => expect(router.currentRoute.value.query).toEqual(expected))
      await vi.dynamicImportSettled()
      await flushPromises()
    },
  )
  test('题库加载、筛选参数与匿名保护路由', async () => {
    expect(wrapper.text()).toContain('A+B Problem')
    await wrapper.get('input[type=search]').setValue('A+B')
    await wrapper.get('form').trigger('submit')
    await vi.dynamicImportSettled()
    await flushPromises()
    expect(router.currentRoute.value.query.keyword).toBe('A+B')
    await go('/problems/new')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/problems/new')
  })
  test('注册不自动登录，登录失败可重试，登录后恢复页面', async () => {
    await go('/register')
    await wrapper.get('input[autocomplete=username]').setValue('alice')
    await wrapper.get('input[type=email]').setValue('alice@example.com')
    await wrapper.get('input[type=password]').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/login'))
    await vi.dynamicImportSettled()
    await flushPromises()
    expect(session.getToken()).toBeNull()
    expect(wrapper.text()).toContain('注册成功')
    await wrapper.get('input[autocomplete=username]').setValue('alice')
    await wrapper.get('input[type=password]').setValue('wrong')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('用户名或密码错误')
    await wrapper.get('input[type=password]').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await vi.waitFor(() => expect(useAuthStore().user?.username).toBe('alice'))
  })
  test('作者创建题目、添加测试点、提交并轮询至通过', async () => {
    await login()
    await go('/problems/new')
    await wrapper.get('input[maxlength="128"]').setValue('A+B Problem')
    await wrapper.get('textarea[maxlength="200000"]').setValue('计算 a+b')
    await wrapper.get('form.editor-form').trigger('submit')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/problems/42/testcases'))
    await vi.dynamicImportSettled()
    await flushPromises()
    const add = wrapper.findAll('form').find((x) => x.text().includes('追加测试点'))!
    await add.findAll('textarea')[0]!.setValue('1 2\n')
    await add.findAll('textarea')[1]!.setValue('3\n')
    await add.trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('测试点已添加')
    expect(testcases.length).toBe(1)
    await go('/problems/42')
    await wrapper.get('.editor-toolbar select').setValue('JAVA')
    await setCode('public class Main {}')
    await wrapper.get('.code-panel form').trigger('submit')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/submissions/99'))
    await vi.dynamicImportSettled()
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.text()).toContain('通过'), { timeout: 2500 })
    expect(wrapper.text()).toContain('public class Main')
    const count = submissions
    await go('/problems')
    await new Promise((resolve) => setTimeout(resolve, 900))
    expect(submissions).toBe(count)
  })
  test('编辑保留完整可选字段、样例与标签', async () => {
    await login()
    await go('/problems/42/edit')
    await wrapper.get('input[maxlength="128"]').setValue('修改后的标题')
    await wrapper.get('form.editor-form').trigger('submit')
    await vi.waitFor(() =>
      expect(requests.some((r) => r.path === '/problems/42' && r.method === 'PUT')).toBe(true),
    )
    const body = requests.find((r) => r.path === '/problems/42' && r.method === 'PUT')!
      .body as Record<string, unknown>
    expect(body.source).toBe('俱乐部')
    expect(body.hint).toBe('使用整数')
    expect(body.tags).toEqual(['数学'])
    expect(body.samples).toEqual([{ input: '1 2\n', output: '3\n', explanation: '1+2=3' }])
  })
  test('匿名提交详情隐藏源码，Markdown 清洗危险 HTML', async () => {
    await go('/submissions/99')
    expect(wrapper.text()).toContain('源码未公开')
    expect(wrapper.find('.source-code').exists()).toBe(false)
    description =
      '<script>window.bad=1</script><img src="x" onerror="window.bad=1"><a href="javascript:alert(1)">链接</a>'
    await go('/problems/42')
    expect(wrapper.find('.markdown script').exists()).toBe(false)
    expect(wrapper.find('.markdown [onerror]').exists()).toBe(false)
    expect(wrapper.find('.markdown a').attributes('href')).toBeUndefined()
  })
  test('管理员自身操作禁用，封禁和解封不猜测现有状态', async () => {
    await login('admin')
    await go('/admin/users')
    const rows = wrapper.findAll('tbody tr')
    expect(
      rows[0]!.findAll('button').filter((x) => x.attributes('disabled') !== undefined).length,
    ).toBeGreaterThanOrEqual(3)
    const ban = rows[1]!.findAll('button').find((x) => x.text() === '封禁')!
    await ban.trigger('click')
    await flushPromises()
    const dialogs = wrapper.findAll('dialog')
    const dialog = dialogs.find((x) => x.text().includes('确认封禁 alice'))!
    const buttons = dialog.findAll('button')
    await buttons[buttons.length - 1]!.trigger('click')
    await flushPromises()
    expect(requests).toContainEqual({
      path: '/admin/users/1/status',
      method: 'PATCH',
      body: { status: 'BANNED' },
    })
    expect(wrapper.text()).not.toContain('已封禁')
  })
  test('接口错误可见并可重试', async () => {
    failProblems = true
    await go('/problems?keyword=fail')
    expect(wrapper.text()).toContain('服务器内部错误')
    failProblems = false
    await wrapper.get('.state.error button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('A+B Problem')
  })
  test('普通用户无法进入管理路由，退出后公开详情清除私密代码', async () => {
    await login()
    await go('/admin/users')
    expect(router.currentRoute.value.path).toBe('/forbidden')
    await go('/submissions/99')
    expect(wrapper.find('.source-code').exists()).toBe(true)
    const logout = wrapper.findAll('header button').find((button) => button.text() === '退出')!
    await logout.trigger('click')
    await flushPromises()
    await vi.dynamicImportSettled()
    await flushPromises()
    expect(wrapper.find('.source-code').exists()).toBe(false)
    expect(wrapper.text()).toContain('源码未公开')
  })
  test('资源新请求覆盖旧请求，销毁后取消且不回写', async () => {
    const pending: { resolve: (value: string) => void; signal: AbortSignal }[] = []
    const scope = effectScope()
    const resource = scope.run(() =>
      useResource((signal) => new Promise<string>((resolve) => pending.push({ resolve, signal }))),
    )!
    void resource.reload()
    expect(pending[0]!.signal.aborted).toBe(true)
    pending[1]!.resolve('new')
    await flushPromises()
    pending[0]!.resolve('old')
    await flushPromises()
    expect(resource.data.value).toBe('new')
    void resource.reload()
    scope.stop()
    expect(pending[2]!.signal.aborted).toBe(true)
    pending[2]!.resolve('late')
    await flushPromises()
    expect(resource.data.value).toBe('new')
  })
})
