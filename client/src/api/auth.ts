import { useState } from "react";
import { queryOptions, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, get, post } from "@/api/client";
import { clearToken, setToken, useToken } from "@/api/token";

export type CurrentUser = {
  id: number;
  username: string;
  nickname: string;
  role: "USER" | "ADMIN";
  createdAt: string;
};

export type CaptchaPurpose = "LOGIN" | "EMAIL_SENDING";

type Captcha = { captchaId: string; image: string; expiresInSeconds: number };

/** Omitted when the deployment turns the image captcha off. */
type CaptchaAnswer = { captchaId?: string; captchaCode?: string };

type LoginResult = {
  token: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: CurrentUser;
};

export type EmailCodeSent = {
  expiresInSeconds: number;
  resendAfterSeconds: number;
};

// Keyed by token so a new sign-in never reuses the previous session's cached user.
function meQuery(token: string | null) {
  return queryOptions({
    queryKey: ["me", token],
    queryFn: async ({ signal }): Promise<CurrentUser | null> => {
      try {
        return await get<CurrentUser>("/auth/me", {}, signal);
      } catch (error) {
        // An expired token: clearing it switches every useSession caller to signed out.
        if (error instanceof ApiError && error.code === 1002) {
          clearToken();
          return null;
        }
        throw error;
      }
    },
    enabled: token !== null,
    staleTime: 5 * 60_000,
    retry: false,
  });
}

/** The signed-in state and, once /auth/me answers, the current user. */
export function useSession() {
  const token = useToken();
  const { data: user } = useQuery(meQuery(token));
  return { signedIn: token !== null, user: user ?? undefined };
}

/** Signs in and seeds the session cache, so the header shows the user without a /auth/me round trip. */
export function useSignIn() {
  const queryClient = useQueryClient();
  return async (
    body: { account: string; password: string } & CaptchaAnswer,
    persist: boolean,
  ) => {
    const result = await post<LoginResult>("/auth/login", body);
    queryClient.setQueryData(meQuery(result.token).queryKey, result.user);
    setToken(result.token, persist);
  };
}

export function sendRegisterCode(
  body: { email: string } & CaptchaAnswer,
): Promise<EmailCodeSent> {
  return post<EmailCodeSent>("/auth/email-codes", body);
}

export function register(body: {
  username: string;
  email: string;
  password: string;
  emailCode: string;
}): Promise<CurrentUser> {
  return post<CurrentUser>("/auth/register", body);
}

function expiryMs(data: Captcha | undefined) {
  return data ? data.expiresInSeconds * 1000 : 0;
}

/**
 * A one-time image captcha and the code typed for it. `enabled` turns false when the deployment
 * has the captcha off (`POST /captcha` answers 1000); callers then omit the captcha fields.
 */
export function useCaptcha(purpose: CaptchaPurpose) {
  const [locked, setLocked] = useState(false);
  const query = useQuery({
    queryKey: ["captcha", purpose],
    // No abort signal: StrictMode's dev remount would cancel the first request and show a
    // failed (canceled) /captcha; without it the remount reuses the in-flight request.
    queryFn: () => post<Captcha>("/captcha", { purpose }),
    // Fresh until the server expires it: then the interval swaps it, or the next window focus
    // does if the tab was in the background. A locked captcha is already used and stays put.
    enabled: !locked,
    staleTime: (query) => expiryMs(query.state.data),
    refetchInterval: (query) => expiryMs(query.state.data) || false,
    gcTime: 0,
    retry: false,
  });
  const captchaId = query.data?.captchaId;

  // The typed code belongs to one captcha, so a new image starts with an empty input.
  const [input, setInput] = useState({ captchaId, code: "" });
  const code = input.captchaId === captchaId ? input.code : "";

  const enabled = !(
    query.error instanceof ApiError && query.error.code === 1000
  );
  return {
    captcha: query.data,
    enabled,
    failed: query.isError,
    code,
    setCode: (next: string) => setInput({ captchaId, code: next }),
    /** True after `lock()`: the server accepted this captcha, so it is shown read-only. */
    locked,
    lock: () => setLocked(true),
    /** Fetches a new captcha and unlocks the field. */
    refresh: () => {
      setLocked(false);
      void query.refetch();
    },
    /** The request fields for the typed code; empty when the captcha is off. */
    answer: (): CaptchaAnswer =>
      enabled ? { captchaId, captchaCode: code.trim() } : {},
  };
}
