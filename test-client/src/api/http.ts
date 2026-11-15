import type { ApiEnvelope } from './types'
import { clearToken, getToken } from './token'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/+$/, '')

export type QueryValue = string | number | boolean | null | undefined | Array<string | number>

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  query?: Record<string, QueryValue>
  body?: unknown
}

export class ApiError extends Error {
  readonly code: number
  readonly status: number

  constructor(code: number, status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return String(error)
}

function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = `${BASE_URL}${path.startsWith('/') ? path : `/${path}`}`
  if (!query) return url

  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item !== undefined && item !== null && item !== '') params.append(key, String(item))
      }
    } else {
      params.append(key, String(value))
    }
  }

  const serialized = params.toString()
  return serialized ? `${url}?${serialized}` : url
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(-1, 0, '网络请求失败，请确认后端已启动且代理地址正确')
  }

  const text = await response.text()
  let envelope: ApiEnvelope<T> | null = null
  if (text) {
    try {
      envelope = JSON.parse(text) as ApiEnvelope<T>
    } catch {
      throw new ApiError(-1, response.status, `响应不是合法 JSON（HTTP ${response.status}）`)
    }
  }

  if (!envelope) {
    if (response.status === 502 || response.status === 503 || response.status === 504) {
      throw new ApiError(
        -1,
        response.status,
        `后端不可达（HTTP ${response.status}），请确认 server 已在 8080 启动`,
      )
    }
    throw new ApiError(-1, response.status, `空响应（HTTP ${response.status}）`)
  }

  if (envelope.code !== 0) {
    if (response.status === 401 && envelope.code === 1002) {
      clearToken()
      window.dispatchEvent(new Event('ioj:auth-expired'))
    }
    throw new ApiError(envelope.code, response.status, envelope.message)
  }

  return envelope.data
}
