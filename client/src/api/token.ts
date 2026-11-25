import { useSyncExternalStore } from "react";

const TOKEN_KEY = "ioj-token";
const listeners = new Set<() => void>();
let current: string | null = readToken();

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

// A session-only sign-in goes to sessionStorage, so closing the browser signs out.
function writeToken(token: string | null, persist: boolean) {
  try {
    localStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    if (token !== null) {
      (persist ? localStorage : sessionStorage).setItem(TOKEN_KEY, token);
    }
  } catch {
    // Storage can be unavailable in private or restricted contexts.
  }
}

function notify() {
  for (const listener of listeners) listener();
}

export function getToken(): string | null {
  return current;
}

/** Stores the token; `persist: false` keeps it only until the browser closes. */
export function setToken(token: string, persist: boolean): void {
  current = token;
  writeToken(token, persist);
  notify();
}

export function clearToken(): void {
  current = null;
  writeToken(null, false);
  notify();
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The stored bearer token; re-renders the caller on sign-in and sign-out. */
export function useToken(): string | null {
  return useSyncExternalStore(subscribe, getToken, getToken);
}
