type Query = Record<string, string | number | boolean | null | undefined>
interface ClientOptions {
  baseUrl?: string
  fetchImpl?: typeof fetch
  getToken?: () => string | null
  onUnauthorized?: (token: string | null) => void
  timeoutMs?: number
}
interface RequestOptions {
  method?: string
  body?: unknown
  query?: Query
  signal?: AbortSignal
}
export class ApiError extends Error {
  code: number
  status: number
  constructor(message: string, code = 0, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}
export function createApiClient(options: ClientOptions = {}) {
  const base = (options.baseUrl ?? '/api/v1').replace(/\/$/, '')
  const transport = options.fetchImpl ?? globalThis.fetch.bind(globalThis)
  return async function request<T>(path: string, init: RequestOptions = {}): Promise<T> {
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(init.query ?? {})) {
      if (value !== '' && value != null) params.set(key, String(value))
    }
    const token = options.getToken?.() ?? null
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (token) headers.Authorization = 'Bearer ' + token
    if (init.body !== undefined) headers['Content-Type'] = 'application/json'
    let response: Response
    const timeout = AbortSignal.timeout(options.timeoutMs ?? 15000)
    const signal = init.signal ? AbortSignal.any([init.signal, timeout]) : timeout
    try {
      response = await transport(base + path + (params.size ? '?' + params : ''), {
        method: init.method ?? 'GET',
        headers,
        signal,
        body: init.body === undefined ? undefined : JSON.stringify(init.body),
      })
    } catch (error) {
      if (init.signal?.aborted) throw error
      if (timeout.aborted) throw new ApiError('请求超时，请重试')
      if (error instanceof Error && error.name === 'AbortError') throw error
      throw new ApiError('无法连接服务，请稍后重试')
    }
    let payload: { code: number; message: string; data: T }
    try {
      payload = await response.json()
    } catch {
      throw new ApiError('服务响应异常（HTTP ' + response.status + '）', 0, response.status)
    }
    if (response.status === 401 && payload.code === 1002) options.onUnauthorized?.(token)
    if (!response.ok || payload.code !== 0)
      throw new ApiError(payload.message || '请求失败', payload.code, response.status)
    return payload.data
  }
}
