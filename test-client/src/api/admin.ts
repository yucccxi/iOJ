import { api } from './http'
import type {
  AdminProblemQuery,
  JudgeKeyCreateRequest,
  JudgeKeyCreatedVO,
  JudgeKeyStatusRequest,
  JudgeKeyVO,
  JudgeNodeVO,
  PageResult,
  ProblemSummaryVO,
  ScopeMetaVO,
  ScopeSaveRequest,
  TagMetaVO,
  TagSaveRequest,
  UpdateProblemStatusRequest,
  UpdateUserRoleRequest,
  UpdateUserStatusRequest,
  UserQuery,
  UserVO,
} from './types'

export function listAdminProblems(
  query: AdminProblemQuery,
): Promise<PageResult<ProblemSummaryVO>> {
  return api<PageResult<ProblemSummaryVO>>('/admin/problems', { query: { ...query } })
}

export function updateProblemStatus(id: number, payload: UpdateProblemStatusRequest): Promise<null> {
  return api<null>(`/admin/problems/${id}/status`, { method: 'PATCH', body: payload })
}

export function listAdminUsers(query: UserQuery): Promise<PageResult<UserVO>> {
  return api<PageResult<UserVO>>('/admin/users', { query: { ...query } })
}

export function updateUserRole(id: number, payload: UpdateUserRoleRequest): Promise<null> {
  return api<null>(`/admin/users/${id}/role`, { method: 'PATCH', body: payload })
}

export function updateUserStatus(id: number, payload: UpdateUserStatusRequest): Promise<null> {
  return api<null>(`/admin/users/${id}/status`, { method: 'PATCH', body: payload })
}

export function createTag(payload: TagSaveRequest): Promise<TagMetaVO> {
  return api<TagMetaVO>('/admin/tags', { method: 'POST', body: payload })
}

export function renameTag(id: number, payload: TagSaveRequest): Promise<TagMetaVO> {
  return api<TagMetaVO>(`/admin/tags/${id}`, { method: 'PUT', body: payload })
}

export function deleteTag(id: number, force: boolean): Promise<null> {
  return api<null>(`/admin/tags/${id}`, { method: 'DELETE', query: { force } })
}

export function createScope(payload: ScopeSaveRequest): Promise<ScopeMetaVO> {
  return api<ScopeMetaVO>('/admin/scopes', { method: 'POST', body: payload })
}

export function renameScope(id: number, payload: ScopeSaveRequest): Promise<ScopeMetaVO> {
  return api<ScopeMetaVO>(`/admin/scopes/${id}`, { method: 'PUT', body: payload })
}

export function deleteScope(id: number): Promise<null> {
  return api<null>(`/admin/scopes/${id}`, { method: 'DELETE' })
}

export function listJudgeKeys(): Promise<JudgeKeyVO[]> {
  return api<JudgeKeyVO[]>('/admin/judge-keys')
}

export function createJudgeKey(payload: JudgeKeyCreateRequest): Promise<JudgeKeyCreatedVO> {
  return api<JudgeKeyCreatedVO>('/admin/judge-keys', { method: 'POST', body: payload })
}

export function updateJudgeKey(id: number, payload: JudgeKeyStatusRequest): Promise<null> {
  return api<null>(`/admin/judge-keys/${id}`, { method: 'PATCH', body: payload })
}

export function deleteJudgeKey(id: number): Promise<null> {
  return api<null>(`/admin/judge-keys/${id}`, { method: 'DELETE' })
}

export function listJudgeNodes(): Promise<JudgeNodeVO[]> {
  return api<JudgeNodeVO[]>('/admin/judge-nodes')
}
