import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { createApiClient, ApiError } from '../src/api/client.ts'

if (!process.argv.includes('--isolated'))
  throw new Error('仅可用于本任务的隔离 H2 服务，请显式传入 --isolated')
const baseUrl =
  process.argv.find((arg) => arg.startsWith('http')) || 'http://127.0.0.1:18080/api/v1'
if (!['http://127.0.0.1:18080/api/v1', 'http://127.0.0.1:5174/api/v1'].includes(baseUrl))
  throw new Error('仅允许本任务的本地隔离端口')
let token = null,
  count = 0
const request = createApiClient({ baseUrl, getToken: () => token })
const call = async (path, options) => {
  const data = await request(path, options)
  count++
  return data
}
const guest = createApiClient({ baseUrl })
const suffix = randomBytes(5).toString('hex')
const username = 'frontend_' + suffix
const password = randomBytes(16).toString('hex')
const post = (path, body) => call(path, { method: 'POST', body })
const patch = (path, body) => call(path, { method: 'PATCH', body })
const put = (path, body) => call(path, { method: 'PUT', body })
assert.equal((await call('/meta/languages')).length, 4)
assert.ok(Array.isArray(await call('/meta/tags')))
const user = await post('/auth/register', { username, email: username + '@example.test', password })
assert.equal(user.role, 'USER')
token = (await post('/auth/login', { account: username, password })).token
assert.equal((await call('/auth/me')).id, user.id)
assert.equal((await call('/users/me')).solvedCount, 0)
assert.equal((await guest('/users/' + user.id)).username, username)
await patch('/users/me', { nickname: '接口验收' })
await put('/users/me/password', { oldPassword: password, newPassword: password + 'x' })
token = (await post('/auth/login', { account: username, password: password + 'x' })).token
const userToken = token
const original = {
  title: '接口验收 ' + suffix,
  difficulty: 'EASY',
  description: '计算 a+b',
  inputFormat: '两个整数',
  outputFormat: '一个整数',
  hint: '',
  source: '隔离验收',
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  tags: ['验收'],
  samples: [{ input: '1 2\n', output: '3\n', explanation: '1+2=3' }],
}
const problem = await post('/problems', original)
const path = '/problems/' + problem.id
await put(path, { ...original, title: original.title + ' updated' })
assert.equal((await call(path)).source, '隔离验收')
assert.equal((await call('/problems', { query: { keyword: suffix } })).total, 1)
assert.equal((await call('/problems/mine', { query: { keyword: suffix } })).total, 1)
const testcase = await post(path + '/testcases', { input: '1 2\n', output: '3\n' })
assert.equal((await call(path + '/testcases/' + testcase.id)).input, '1 2\n')
await call(path + '/testcases/' + testcase.id, { method: 'DELETE' })
await put(path + '/testcases', [{ input: '1 2\n', output: '3\n' }])
assert.equal((await call(path + '/testcases')).length, 1)
const submission = await post('/submissions', {
  problemId: problem.id,
  language: 'JAVA',
  code: 'public class Main {}',
})
assert.equal(submission.status, 'PENDING')
assert.ok((await call('/submissions/' + submission.id)).code)
assert.equal((await guest('/submissions/' + submission.id)).code, null)
assert.equal((await call('/submissions', { query: { userId: user.id } })).total, 1)
token = (await post('/auth/login', { account: 'admin', password: 'admin123456' })).token
assert.equal((await call('/admin/users', { query: { keyword: username } })).total, 1)
assert.equal((await call('/admin/problems', { query: { keyword: suffix } })).total, 1)
await patch('/admin/problems/' + problem.id + '/status', { status: 'HIDDEN' })
await assert.rejects(
  () => guest(path),
  (error) => error instanceof ApiError && error.code === 3002,
)
await patch('/admin/problems/' + problem.id + '/status', { status: 'PUBLISHED' })
await patch('/admin/users/' + user.id + '/role', { role: 'ADMIN' })
await patch('/admin/users/' + user.id + '/status', { status: 'BANNED' })
const banned = createApiClient({ baseUrl, getToken: () => userToken })
await assert.rejects(
  () => banned('/auth/me'),
  (error) => error instanceof ApiError && error.code === 1002,
)
await patch('/admin/users/' + user.id + '/status', { status: 'ACTIVE' })
assert.equal((await banned('/auth/me')).role, 'ADMIN')
await patch('/admin/users/' + user.id + '/role', { role: 'USER' })
await call(path, { method: 'DELETE' })
console.log(
  '真实 HTTP 契约联调通过：' +
    count +
    ' 次请求；匿名字段裁剪、审核、角色、封禁验证通过。判题 worker 关闭，提交保持 PENDING。',
)
