export function fmt(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

export function fmtDate(value: string | null | undefined): string {
  if (!value) return '-'
  return value.replace('T', ' ')
}

export function fmtRating(value: number): string {
  return value < 0 ? '未评分' : String(value)
}

export function fmtBytes(value: number): string {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`
  return `${(value / 1024 / 1024).toFixed(1)} MiB`
}

export function statusClass(status: string | undefined | null): string {
  if (!status) return 'badge'
  if (status === 'ACCEPTED' || status === 'PUBLISHED' || status === 'ONLINE' || status === 'ACTIVE') {
    return 'badge badge-green'
  }
  if (
    status === 'WRONG_ANSWER' ||
    status === 'TIME_LIMIT_EXCEEDED' ||
    status === 'MEMORY_LIMIT_EXCEEDED' ||
    status === 'OUTPUT_LIMIT_EXCEEDED' ||
    status === 'RUNTIME_ERROR' ||
    status === 'COMPILATION_ERROR' ||
    status === 'HIDDEN' ||
    status === 'BANNED'
  ) {
    return 'badge badge-red'
  }
  if (status === 'CHECKER_ERROR' || status === 'SYSTEM_ERROR') {
    return 'badge badge-orange'
  }
  if (status === 'PENDING' || status === 'JUDGING' || status === 'PENDING_REVIEW' || status === 'DRAFT') {
    return 'badge badge-gray'
  }
  return 'badge'
}

export function statusLabel(
  value: string,
  items: Array<{ value: string; label: string }>,
): string {
  return items.find((item) => item.value === value)?.label ?? value
}

export function toDatetimeLocal(value: string | null | undefined): string {
  if (!value) return ''
  return value.slice(0, 19)
}

export function fromDatetimeLocal(value: string): string | null {
  if (!value) return null
  return value.length === 16 ? `${value}:00` : value
}

export function parseNumber(value: unknown): number | undefined {
  if (value === null || value === undefined) return undefined
  const text = String(value).trim()
  if (text === '') return undefined
  const parsed = Number(text)
  return Number.isFinite(parsed) ? parsed : undefined
}

export function parseInteger(value: unknown): number | undefined {
  const parsed = parseNumber(value)
  return parsed === undefined ? undefined : Math.trunc(parsed)
}
