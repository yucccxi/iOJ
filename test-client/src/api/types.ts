export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'UNKNOWN'
export type ProblemStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'HIDDEN'
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
  | 'CHECKER_ERROR'
  | 'SYSTEM_ERROR'
export type UserRole = 'USER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'BANNED'
export type CatalogSort = 'DEFAULT' | 'RATING' | 'ACCEPTED' | 'SUBMISSIONS' | 'ACCEPTANCE_RATE'
export type SortOrder = 'ASC' | 'DESC'
export type TagMatchMode = 'ANY' | 'ALL'
export type CaptchaPurpose = 'LOGIN' | 'EMAIL_SENDING'

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface PageQuery {
  page?: number
  size?: number
}

export interface CaptchaVO {
  captchaId: string
  image: string
  expiresInSeconds: number
}

export interface SendEmailCodeVO {
  expiresInSeconds: number
  resendAfterSeconds: number
}

export interface UserVO {
  id: number
  username: string
  nickname: string
  role: UserRole
  createdAt: string
}

export interface UserProfileVO extends UserVO {
  solvedCount: number
  contributedCount: number
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: UserVO
}

export interface TagVO {
  id: number
  name: string
}

export interface TagMetaVO extends TagVO {
  problemCount: number
}

export interface ScopeMetaVO {
  id: number
  name: string
  createdAt: string
  problemCount: number
}

export interface LanguageVO {
  code: string
  displayName: string
}

export interface CatalogItemVO {
  id: number
  title: string
  code: string | null
  difficulty: Difficulty
  time: string | null
  rating: number
  scopeId: number
  scopeName: string
  url: string | null
  submitCount: number
  acceptedCount: number
  authorId: number | null
  authorNickname: string | null
  tags: TagVO[]
  createdAt: string
}

export interface SampleVO {
  ordinal: number
  input: string
  output: string
  explanation: string | null
}

export interface ProblemDetailVO {
  id: number
  title: string
  difficulty: Difficulty
  rating: number
  description: string
  inputFormat: string | null
  outputFormat: string | null
  hint: string | null
  scopeId: number
  scopeName: string
  timeLimitMs: number
  memoryLimitMb: number
  status: ProblemStatus
  authorId: number
  authorNickname: string
  tags: TagVO[]
  samples: SampleVO[]
  submitCount: number
  acceptedCount: number
  solved: boolean
  createdAt: string
  updatedAt: string
  hasChecker: boolean
}

export interface ProblemSummaryVO {
  id: number
  title: string
  difficulty: Difficulty
  rating: number
  status: ProblemStatus
  submitCount: number
  acceptedCount: number
  authorId: number
  authorNickname: string
  tags: TagVO[]
  createdAt: string
}

export interface SubmissionCaseVO {
  ordinal: number
  status: SubmissionStatus
  statusText: string
  timeUsedMs: number | null
  memoryUsedKb: number | null
  message: string | null
}

export interface SubmissionSummaryVO {
  id: number
  problemId: number
  problemTitle: string
  userId: number
  userNickname: string
  language: string
  status: SubmissionStatus
  statusText: string
  timeUsedMs: number | null
  memoryUsedKb: number | null
  createdAt: string
}

export interface SubmissionDetailVO extends SubmissionSummaryVO {
  code: string | null
  compileMessage: string | null
  judgeMessage: string | null
  judgedAt: string | null
  cases: SubmissionCaseVO[]
}

export interface TestcaseSummaryVO {
  id: number
  ordinal: number
  inputBytes: number
  outputBytes: number
  createdAt: string
}

export interface TestcaseDetailVO {
  id: number
  ordinal: number
  input: string
  output: string
}

export interface CheckerDetailVO {
  code: string
}

export interface JudgeKeyVO {
  id: number
  name: string
  prefix: string
  enabled: boolean
  expiresAt: string | null
  lastUsedAt: string | null
  createdAt: string
}

export interface JudgeKeyCreatedVO {
  id: number
  name: string
  prefix: string
  key: string
  expiresAt: string | null
  createdAt: string
}

export interface JudgeNodeVO {
  nodeId: string
  hostname: string | null
  version: string | null
  languages: string[]
  maxConcurrency: number
  runningTasks: number
  isolateVersion: string | null
  status: string
  lastHeartbeatAt: string | null
}

export interface CatalogQuery extends PageQuery {
  keyword?: string
  difficulty?: Difficulty[]
  tagId?: number[]
  tagMatch?: TagMatchMode
  rating?: number
  minRating?: number
  maxRating?: number
  scopeId?: number[]
  sort?: CatalogSort
  order?: SortOrder
}

export interface ProblemQuery extends PageQuery {
  keyword?: string
}

export interface AdminProblemQuery extends PageQuery {
  keyword?: string
  status?: ProblemStatus
}

export interface SubmissionQuery extends PageQuery {
  problemId?: number
  userId?: number
  status?: SubmissionStatus
}

export interface UserQuery extends PageQuery {
  keyword?: string
}

export interface CaptchaRequest {
  purpose: CaptchaPurpose
}

export interface SendEmailCodeRequest {
  email: string
  captchaId?: string
  captchaCode?: string
}

export interface PasswordResetRequest {
  email: string
  emailCode: string
  newPassword: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  nickname?: string | null
  emailCode?: string | null
}

export interface LoginRequest {
  account: string
  password: string
  captchaId?: string
  captchaCode?: string
}

export interface UpdateProfileRequest {
  nickname: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface SamplePayload {
  input: string
  output: string
  explanation?: string | null
}

export interface ProblemSavePayload {
  title: string
  difficulty: Difficulty
  rating?: number | null
  description: string
  inputFormat?: string | null
  outputFormat?: string | null
  hint?: string | null
  timeLimitMs?: number
  memoryLimitMb?: number
  tagIds?: number[]
  samples?: SamplePayload[]
}

export interface TestcasePayload {
  input: string
  output: string
}

export interface SubmitRequest {
  problemId: number
  language: string
  code: string
}

export interface UpdateProblemStatusRequest {
  status: 'PUBLISHED' | 'HIDDEN'
}

export interface UpdateUserRoleRequest {
  role: UserRole
}

export interface UpdateUserStatusRequest {
  status: UserStatus
}

export interface TagSaveRequest {
  name: string
}

export interface ScopeSaveRequest {
  name: string
}

export interface JudgeKeyCreateRequest {
  name: string
  expiresAt?: string | null
}

export interface JudgeKeyStatusRequest {
  enabled: boolean
}

export const DIFFICULTIES: Array<{ value: Difficulty; label: string }> = [
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' },
  { value: 'UNKNOWN', label: '未知（仅外部题）' },
]

export const PROBLEM_STATUSES: Array<{ value: ProblemStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'HIDDEN', label: '已下架' },
]

export const SUBMISSION_STATUSES: Array<{ value: SubmissionStatus; label: string; terminal: boolean }> = [
  { value: 'PENDING', label: '排队中', terminal: false },
  { value: 'JUDGING', label: '评测中', terminal: false },
  { value: 'ACCEPTED', label: '通过', terminal: true },
  { value: 'WRONG_ANSWER', label: '答案错误', terminal: true },
  { value: 'TIME_LIMIT_EXCEEDED', label: '超出时间限制', terminal: true },
  { value: 'MEMORY_LIMIT_EXCEEDED', label: '超出内存限制', terminal: true },
  { value: 'OUTPUT_LIMIT_EXCEEDED', label: '输出超出限制', terminal: true },
  { value: 'RUNTIME_ERROR', label: '运行错误', terminal: true },
  { value: 'COMPILATION_ERROR', label: '编译错误', terminal: true },
  { value: 'CHECKER_ERROR', label: '判题器错误', terminal: true },
  { value: 'SYSTEM_ERROR', label: '系统错误', terminal: true },
]

export const CATALOG_SORTS: Array<{ value: CatalogSort; label: string }> = [
  { value: 'DEFAULT', label: '默认' },
  { value: 'RATING', label: '难度分' },
  { value: 'ACCEPTED', label: '通过数' },
  { value: 'SUBMISSIONS', label: '提交数' },
  { value: 'ACCEPTANCE_RATE', label: '通过率' },
]

export const USER_ROLES: Array<{ value: UserRole; label: string }> = [
  { value: 'USER', label: '普通用户' },
  { value: 'ADMIN', label: '管理员' },
]

export const USER_STATUSES: Array<{ value: UserStatus; label: string }> = [
  { value: 'ACTIVE', label: '正常' },
  { value: 'BANNED', label: '封禁' },
]
