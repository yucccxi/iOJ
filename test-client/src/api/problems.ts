import { api } from './http'
import type {
  CheckerDetailVO,
  PageResult,
  ProblemDetailVO,
  ProblemQuery,
  ProblemSavePayload,
  ProblemSummaryVO,
  TestcaseDetailVO,
  TestcasePayload,
  TestcaseSummaryVO,
} from './types'

export function listMyProblems(query: ProblemQuery): Promise<PageResult<ProblemSummaryVO>> {
  return api<PageResult<ProblemSummaryVO>>('/problems/mine', { query: { ...query } })
}

export function getProblem(id: number): Promise<ProblemDetailVO> {
  return api<ProblemDetailVO>(`/problems/${id}`)
}

export function createProblem(payload: ProblemSavePayload): Promise<ProblemDetailVO> {
  return api<ProblemDetailVO>('/problems', { method: 'POST', body: payload })
}

export function updateProblem(id: number, payload: ProblemSavePayload): Promise<ProblemDetailVO> {
  return api<ProblemDetailVO>(`/problems/${id}`, { method: 'PUT', body: payload })
}

export function deleteProblem(id: number): Promise<null> {
  return api<null>(`/problems/${id}`, { method: 'DELETE' })
}

export function getChecker(id: number): Promise<CheckerDetailVO> {
  return api<CheckerDetailVO>(`/problems/${id}/checker`)
}

export function saveChecker(id: number, code: string): Promise<null> {
  return api<null>(`/problems/${id}/checker`, { method: 'PUT', body: { code } })
}

export function listTestcases(problemId: number): Promise<TestcaseSummaryVO[]> {
  return api<TestcaseSummaryVO[]>(`/problems/${problemId}/testcases`)
}

export function getTestcase(problemId: number, testcaseId: number): Promise<TestcaseDetailVO> {
  return api<TestcaseDetailVO>(`/problems/${problemId}/testcases/${testcaseId}`)
}

export function addTestcase(problemId: number, payload: TestcasePayload): Promise<TestcaseSummaryVO> {
  return api<TestcaseSummaryVO>(`/problems/${problemId}/testcases`, { method: 'POST', body: payload })
}

export function replaceTestcases(
  problemId: number,
  payload: TestcasePayload[],
): Promise<TestcaseSummaryVO[]> {
  return api<TestcaseSummaryVO[]>(`/problems/${problemId}/testcases`, {
    method: 'PUT',
    body: payload,
  })
}

export function deleteTestcase(problemId: number, testcaseId: number): Promise<null> {
  return api<null>(`/problems/${problemId}/testcases/${testcaseId}`, { method: 'DELETE' })
}
