import { useSyncExternalStore } from "react";

export const themes = ["default", "dark", "glass"] as const;
export type Theme = (typeof themes)[number];

const STORAGE_KEY = "ioj-theme";
const listeners = new Set<() => void>();
let current: Theme = readTheme();

function readTheme(): Theme {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return themes.includes(value as Theme) ? (value as Theme) : "default";
  } catch {
    return "default";
  }
}

function applyTheme(theme: Theme) {
  // The default theme is the absence of data-theme; dark and glass are overrides.
  if (theme === "default") delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = theme;
}

applyTheme(current);

export function getTheme(): Theme {
  return current;
}

export function setTheme(theme: Theme): void {
  current = theme;
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // Storage can be unavailable in private or restricted contexts.
  }
  applyTheme(theme);
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The active theme; re-renders the caller when it changes. */
export function useTheme(): Theme {
  return useSyncExternalStore(subscribe, getTheme, getTheme);
}
