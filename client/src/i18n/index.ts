import { useSyncExternalStore } from "react";
import { en } from "./en";
import { zh } from "./zh";

export const locales = ["zh-CN", "en"] as const;
export type Locale = (typeof locales)[number];
export type Copy = typeof zh | typeof en;

const STORAGE_KEY = "ioj-locale";
const listeners = new Set<() => void>();
let current: Locale = readLocale();

function readLocale(): Locale {
  try {
    return localStorage.getItem(STORAGE_KEY) === "en" ? "en" : "zh-CN";
  } catch {
    return "zh-CN";
  }
}

function applyLocale(locale: Locale) {
  document.documentElement.lang = locale;
}

applyLocale(current);

export function getLocale(): Locale {
  return current;
}

/** Copy for the current locale, for code that runs outside a component. */
export function getCopy(): Copy {
  return current === "en" ? en : zh;
}

export function setLocale(locale: Locale): void {
  current = locale;
  try {
    localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    // Storage can be unavailable in private or restricted contexts.
  }
  applyLocale(locale);
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The active locale; re-renders the caller when it changes. */
export function useLocale(): Locale {
  return useSyncExternalStore(subscribe, getLocale, getLocale);
}

/** The dictionary for the active locale; re-renders the caller when it changes. */
export function useCopy(): Copy {
  const locale = useLocale();
  return locale === "en" ? en : zh;
}
