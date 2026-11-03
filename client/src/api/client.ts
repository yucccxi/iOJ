type Envelope<T> = { code: number; message: string; data: T };
type Query = Record<string, string | number | undefined>;

/** A failed request; `code` is the backend business code from docs/shared/api/01-conventions.md. */
export class ApiError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

/** GETs `/api/v1{path}` and returns the envelope's `data`; empty query values are dropped. */
export async function get<T>(
  path: string,
  query: Query = {},
  signal?: AbortSignal,
): Promise<T> {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== "") params.set(key, String(value));
  }
  const search = params.size > 0 ? `?${params}` : "";
  const response = await fetch(`/api/v1${path}${search}`, { signal });

  // A proxy or gateway error returns HTML, not the envelope.
  if (!response.headers.get("content-type")?.includes("application/json")) {
    throw new ApiError(-1, `服务暂时不可用（HTTP ${response.status}）`);
  }
  const body = (await response.json()) as Envelope<T>;
  // Only code 0 means success; the same business error can come with 400, 405 or 415.
  if (body.code !== 0) throw new ApiError(body.code, body.message);
  return body.data;
}
