import { createApiClient } from './client'
import { session } from './session'
import type {
  User,
  Profile,
  LoginResult,
  Page,
  ProblemSummary,
  Problem,
  ProblemInput,
  Tag,
  LanguageOption,
  Language,
  Submission,
  SubmissionSummary,
  Testcase,
  TestcaseInput,
  TestcaseSummary,
  Role,
  ProblemStatus,
} from '@/types/api'

export const request = createApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  getToken: session.getToken,
  onUnauthorized: (token) => session.clearIfMatches(token),
})
type Query = Record<string, string | number | undefined>
export const api = {
  login: (body: { account: string; password: string }, signal?: AbortSignal) =>
    request<LoginResult>('/auth/login', { method: 'POST', body, signal }),
  register: (
    body: { username: string; email: string; password: string; nickname?: string },
    signal?: AbortSignal,
  ) => request<User>('/auth/register', { method: 'POST', body, signal }),
  me: () => request<User>('/auth/me'),
  profile: (id: string | number, signal?: AbortSignal) =>
    request<Profile>('/users/' + id, { signal }),
  updateProfile: (nickname: string, signal?: AbortSignal) =>
    request<User>('/users/me', { method: 'PATCH', body: { nickname }, signal }),
  password: (body: { oldPassword: string; newPassword: string }, signal?: AbortSignal) =>
    request<null>('/users/me/password', { method: 'PUT', body, signal }),
  problems: (query: Query, signal?: AbortSignal) =>
    request<Page<ProblemSummary>>('/problems', { query, signal }),
  mine: (query: Query, signal?: AbortSignal) =>
    request<Page<ProblemSummary>>('/problems/mine', { query, signal }),
  problem: (id: string | number, signal?: AbortSignal) =>
    request<Problem>('/problems/' + id, { signal }),
  saveProblem: (id: string | undefined, body: ProblemInput, signal?: AbortSignal) =>
    request<Problem>('/problems' + (id ? '/' + id : ''), {
      method: id ? 'PUT' : 'POST',
      body,
      signal,
    }),
  deleteProblem: (id: number, signal?: AbortSignal) =>
    request<null>('/problems/' + id, { method: 'DELETE', signal }),
  tags: (signal?: AbortSignal) => request<Tag[]>('/meta/tags', { signal }),
  languages: (signal?: AbortSignal) => request<LanguageOption[]>('/meta/languages', { signal }),
  submit: (body: { problemId: number; language: Language; code: string }, signal?: AbortSignal) =>
    request<Submission>('/submissions', { method: 'POST', body, signal }),
  submissions: (query: Query, signal?: AbortSignal) =>
    request<Page<SubmissionSummary>>('/submissions', { query, signal }),
  submission: (id: string | number, signal?: AbortSignal) =>
    request<Submission>('/submissions/' + id, { signal }),
  testcases: (id: string | number, signal?: AbortSignal) =>
    request<TestcaseSummary[]>('/problems/' + id + '/testcases', { signal }),
  testcase: (problemId: string, id: number, signal?: AbortSignal) =>
    request<Testcase>('/problems/' + problemId + '/testcases/' + id, { signal }),
  addTestcase: (id: string, body: TestcaseInput, signal?: AbortSignal) =>
    request<TestcaseSummary>('/problems/' + id + '/testcases', { method: 'POST', body, signal }),
  replaceTestcases: (id: string, body: TestcaseInput[], signal?: AbortSignal) =>
    request<TestcaseSummary[]>('/problems/' + id + '/testcases', { method: 'PUT', body, signal }),
  deleteTestcase: (problemId: string, id: number, signal?: AbortSignal) =>
    request<null>('/problems/' + problemId + '/testcases/' + id, { method: 'DELETE', signal }),
  adminProblems: (query: Query, signal?: AbortSignal) =>
    request<Page<ProblemSummary>>('/admin/problems', { query, signal }),
  problemStatus: (id: number, status: ProblemStatus, signal?: AbortSignal) =>
    request<null>('/admin/problems/' + id + '/status', {
      method: 'PATCH',
      body: { status },
      signal,
    }),
  users: (query: Query, signal?: AbortSignal) =>
    request<Page<User>>('/admin/users', { query, signal }),
  userRole: (id: number, role: Role, signal?: AbortSignal) =>
    request<null>('/admin/users/' + id + '/role', { method: 'PATCH', body: { role }, signal }),
  userStatus: (id: number, status: 'ACTIVE' | 'BANNED', signal?: AbortSignal) =>
    request<null>('/admin/users/' + id + '/status', { method: 'PATCH', body: { status }, signal }),
}
