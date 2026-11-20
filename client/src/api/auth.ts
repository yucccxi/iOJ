import { queryOptions } from "@tanstack/react-query";
import { ApiError, get } from "@/api/client";
import { clearToken, getToken } from "@/api/token";

export type CurrentUser = {
  id: number;
  username: string;
  nickname: string;
  role: "USER" | "ADMIN";
  createdAt: string;
};

export const meQueryKey = ["me"] as const;

/**
 * The signed-in user, or null after a 1002 clears an expired stored token.
 * Keep this disabled when no token exists so the menu can show the signed-out state.
 */
export const meQuery = queryOptions({
  queryKey: meQueryKey,
  queryFn: async ({ signal }): Promise<CurrentUser | null> => {
    try {
      return await get<CurrentUser>("/auth/me", {}, signal);
    } catch (error) {
      if (error instanceof ApiError && error.code === 1002) {
        clearToken();
        return null;
      }
      throw error;
    }
  },
  staleTime: 5 * 60_000,
  retry: false,
});

export function hasToken(): boolean {
  return getToken() !== null;
}
