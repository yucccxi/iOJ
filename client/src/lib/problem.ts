import type { CatalogItem } from "@/api/types";

// The catalog has no field for a platform's own problem number, so it is read from the link.
const externalCodePatterns: [RegExp, (match: RegExpMatchArray) => string][] = [
  [/codeforces\.com\/(?:problemset\/problem|contest|gym)\/(\d+)\/(?:problem\/)?(\w+)/, (m) => `${m[1]}${m[2]}`],
  [/luogu\.com\.cn\/problem\/([A-Z]+\d+)/, (m) => m[1]],
  [/atcoder\.jp\/contests\/\w+\/tasks\/([a-z]+\d+)_(\w+)/, (m) => `${m[1].toUpperCase()} ${m[2].toUpperCase()}`],
];

/** Display ID: `U{id}` for problems uploaded here, the platform's number for external ones. */
export function problemCode(item: CatalogItem) {
  if (item.kind === "LOCAL") return `U${item.id}`;
  for (const [pattern, format] of externalCodePatterns) {
    const match = item.url?.match(pattern);
    if (match) return format(match);
  }
  return "—";
}
