import type { Widen } from "./types";
import type { zh } from "./zh";

/** User-visible copy for the en locale. */
export const en = {
  nav: {
    home: "Home",
    problems: "Problems",
    discuss: "Discuss",
    submissions: "Submissions",
    about: "About",
    switchSection: "Switch section",
    contribute: "Contribute",
    login: "Sign in",
  },
  account: {
    menuLabel: "Account menu",
    signedOutPrompt:
      "Sign in to submit code, bookmark problems, and see activity",
    signUp: "Sign up",
    profile: "Profile",
    activity: "Activity",
    bookmarks: "Bookmarks",
    settings: "Settings",
    signOut: "Sign out",
    uid: (id: number) => `UID ${id}`,
    loading: "Loading",
  },
  theme: {
    label: "Theme",
    default: "Default",
    dark: "Dark",
    glass: "Glass",
  },
  language: {
    label: "Language",
    zh: "简体中文",
    en: "English",
  },
  select: {
    current: (name: string, value: string) => `${name}: ${value}`,
  },
  tagFilter: {
    label: "Filter by tags",
    title: "Tags",
    trigger: (count: number) => (count > 0 ? `Tags · ${count}` : "Tags"),
    searchPlaceholder: "Search tags...",
    searchLabel: "Search tags",
    empty: "No matching tags",
    matchMode: "Match mode",
    any: "Any",
    all: "All",
    selected: (count: number) => `${count} tags selected`,
    reset: "Reset",
    apply: "Apply",
  },
  page: {
    problemDetail: "Problem details",
    profile: "Profile",
    activity: "Activity",
    bookmarks: "Bookmarks",
    settings: "Settings",
  },
  errors: {
    serviceUnavailable: (status: number) =>
      `Service temporarily unavailable (HTTP ${status})`,
  },
} satisfies Widen<typeof zh>;
