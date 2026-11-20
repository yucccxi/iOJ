import { queryOptions, useQuery } from "@tanstack/react-query";
import { ApiError, get } from "@/api/client";
import { clearToken, useToken } from "@/api/token";

export type CurrentUser = {
  id: number;
  username: string;
  nickname: string;
  role: "USER" | "ADMIN";
  createdAt: string;
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
