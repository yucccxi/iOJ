import type { Widen } from "./types";
import { zh } from "./zh";

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
  },
  errors: {
    serviceUnavailable: (status: number) =>
      `Service temporarily unavailable (HTTP ${status})`,
  },
} satisfies Widen<typeof zh>;
