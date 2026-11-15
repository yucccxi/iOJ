import { api } from './http'
import type {
  PageResult,
  SubmissionDetailVO,
  SubmissionQuery,
  SubmissionSummaryVO,
  SubmitRequest,
} from './types'

export function listSubmissions(query: SubmissionQuery): Promise<PageResult<SubmissionSummaryVO>> {
  return api<PageResult<SubmissionSummaryVO>>('/submissions', { query: { ...query } })
}

export function getSubmission(id: number): Promise<SubmissionDetailVO> {
  return api<SubmissionDetailVO>(`/submissions/${id}`)
}

export function submit(payload: SubmitRequest): Promise<SubmissionDetailVO> {
  return api<SubmissionDetailVO>('/submissions', { method: 'POST', body: payload })
}
