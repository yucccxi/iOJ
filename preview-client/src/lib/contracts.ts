import type { TestcaseInput, ProblemInput } from '../types/api.ts'
export function safeReturn(value: unknown): string {
  return typeof value === 'string' &&
    value.startsWith('/') &&
    !value.startsWith('//') &&
    !value.includes('\\') &&
    !/[\u0000-\u001f]/.test(value) &&
    !/^\/(login|register)(\/|[?#]|$)/.test(value)
    ? value
    : '/problems'
}
export function problemInput(problem: ProblemInput): ProblemInput {
  return {
    title: problem.title,
    difficulty: problem.difficulty,
    description: problem.description,
    inputFormat: problem.inputFormat ?? null,
    outputFormat: problem.outputFormat ?? null,
    hint: problem.hint ?? null,
    source: problem.source ?? null,
    timeLimitMs: problem.timeLimitMs,
    memoryLimitMb: problem.memoryLimitMb,
    tags: [...problem.tags],
    samples: problem.samples.map((s) => ({
      input: s.input,
      output: s.output,
      explanation: s.explanation ?? null,
    })),
  }
}
export function validateTestcases(value: unknown): TestcaseInput[] {
  if (!Array.isArray(value) || value.length > 100) throw new Error('测试数据需为数组，最多 100 条')
  const encoder = new TextEncoder()
  return value.map((row, i) => {
    if (
      row == null ||
      typeof row !== 'object' ||
      typeof row.input !== 'string' ||
      typeof row.output !== 'string'
    )
      throw new Error('第 ' + (i + 1) + ' 条必须包含文本 input 和 output')
    if (encoder.encode(row.input).length > 1048576 || encoder.encode(row.output).length > 1048576)
      throw new Error('第 ' + (i + 1) + ' 条输入或输出超过 1 MiB')
    return { input: row.input, output: row.output }
  })
}
export function finished(status: string): boolean {
  return status !== 'PENDING' && status !== 'JUDGING'
}
export function parseTestcases(text: string): TestcaseInput[] {
  let value: unknown
  try {
    value = JSON.parse(text)
  } catch {
    throw new Error('JSON 格式不正确')
  }
  return validateTestcases(value)
}
export function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请重试'
}
export function dateText(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}
export const difficultyText = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
export const problemStatusText = {
  DRAFT: '草稿',
  PENDING_REVIEW: '待审核',
  PUBLISHED: '已发布',
  HIDDEN: '已下架',
}
export const statuses = {
  PENDING: '排队中',
  JUDGING: '评测中',
  ACCEPTED: '通过',
  WRONG_ANSWER: '答案错误',
  TIME_LIMIT_EXCEEDED: '超时',
  MEMORY_LIMIT_EXCEEDED: '超出内存限制',
  OUTPUT_LIMIT_EXCEEDED: '输出超限',
  RUNTIME_ERROR: '运行错误',
  COMPILATION_ERROR: '编译错误',
  SYSTEM_ERROR: '系统错误',
}
