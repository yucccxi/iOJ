export type Role = 'USER' | 'ADMIN'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type ProblemStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'HIDDEN'
export type Language = 'JAVA' | 'CPP' | 'C' | 'PYTHON3'
export type SubmissionStatus =
  | 'PENDING'
  | 'JUDGING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'OUTPUT_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'COMPILATION_ERROR'
  | 'SYSTEM_ERROR'
export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}
export interface User {
  id: number
  username: string
  nickname: string
  role: Role
  createdAt: string
}
export interface Profile extends User {
  solvedCount: number
}
export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  user: User
}
export interface Tag {
  id: number
  name: string
}
export interface LanguageOption {
  code: Language
  displayName: string
}
export interface Sample {
  ordinal: number
  input: string
  output: string
  explanation: string | null
}
export interface ProblemSummary {
  id: number
  title: string
  difficulty: Difficulty
  status: ProblemStatus
  submitCount: number
  acceptedCount: number
  authorId: number
  authorNickname: string
  tags: Tag[]
  createdAt: string
}
export interface Problem extends ProblemSummary {
  description: string
  inputFormat: string | null
  outputFormat: string | null
  hint: string | null
  source: string | null
  timeLimitMs: number
  memoryLimitMb: number
  samples: Sample[]
  solved: boolean
  updatedAt: string
}
export interface SampleInput {
  input: string
  output: string
  explanation?: string | null
}
export interface ProblemInput {
  title: string
  difficulty: Difficulty
  description: string
  inputFormat: string | null
  outputFormat: string | null
  hint: string | null
  source: string | null
  timeLimitMs: number
  memoryLimitMb: number
  tags: string[]
  samples: SampleInput[]
}
export interface TestcaseInput {
  input: string
  output: string
}
export interface TestcaseSummary {
  id: number
  ordinal: number
  inputBytes: number
  outputBytes: number
  createdAt: string
}
export interface Testcase extends TestcaseInput {
  id: number
  ordinal: number
}
export interface SubmissionSummary {
  id: number
  problemId: number
  problemTitle: string
  userId: number
  userNickname: string
  language: Language
  status: SubmissionStatus
  statusText: string
  timeUsedMs: number | null
  memoryUsedKb: number | null
  createdAt: string
}
export interface CaseResult {
  ordinal: number
  status: SubmissionStatus
  statusText: string
  timeUsedMs: number | null
  memoryUsedKb: number | null
  message: string | null
}
export interface Submission extends SubmissionSummary {
  code: string | null
  compileMessage: string | null
  judgeMessage: string | null
  judgedAt: string | null
  cases: CaseResult[]
}
