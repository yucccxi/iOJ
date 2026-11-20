import { useSyncExternalStore } from "react";

const TOKEN_KEY = "ioj-token";
const listeners = new Set<() => void>();
let current: string | null = readToken();

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function notify() {
  for (const listener of listeners) listener();
}

export function getToken(): string | null {
  return current;
}

export function setToken(token: string): void {
  current = token;
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // Storage can be unavailable in private or restricted contexts.
  }
  notify();
}

export function clearToken(): void {
  current = null;
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Storage can be unavailable in private or restricted contexts.
  }
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
