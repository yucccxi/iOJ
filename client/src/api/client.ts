import { getToken } from "@/api/token";
import { getCopy } from "@/i18n";

type Envelope<T> = { code: number; message: string; data: T };
type QueryValue = string | number;
type Query = Record<string, QueryValue | readonly QueryValue[] | undefined>;

/** A failed request; `code` is the backend business code from docs/api/README.md. */
export class ApiError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`/api/v1${path}`, { ...init, headers });

  // A proxy or gateway error returns HTML, not the envelope.
  if (!response.headers.get("content-type")?.includes("application/json")) {
    throw new ApiError(
      -1,
      getCopy().errors.serviceUnavailable(response.status),
    );
  }
  const body = (await response.json()) as Envelope<T>;
  // Only code 0 means success; the same business error can come with 400, 405 or 415.
  if (body.code !== 0) throw new ApiError(body.code, body.message);
  return body.data;
}

/**
 * GETs `/api/v1{path}` and returns the envelope's `data`. Arrays become repeated params
 * (`a=1&a=2`); empty values are dropped.
 */
export function get<T>(
  path: string,
  query: Query = {},
  signal?: AbortSignal,
): Promise<T> {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    for (const item of [value].flat()) {
      if (item !== undefined && item !== "") params.append(key, String(item));
    }
  }
  const search = params.size > 0 ? `?${params}` : "";
  return request<T>(`${path}${search}`, { signal });
}

/** POSTs `body` as JSON to `/api/v1{path}` and returns the envelope's `data`. */
export function post<T>(
  path: string,
  body: unknown,
  signal?: AbortSignal,
): Promise<T> {
  return request<T>(path, {
    method: "POST",
    signal,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
