import type { CatalogItem } from "@/api/types";

/**
 * Display ID: `IOJ{id}` for problems uploaded here, the platform's number for external ones.
 * The platform prefix is dropped (`CF_2278G` → `2278G`, `AT_ABC482_G` → `ABC482 G`)
 * because the source column already names the platform.
 */
export function problemCode(item: CatalogItem) {
  if (item.kind === "LOCAL") return `IOJ${item.id}`;
  if (!item.code) return "—";
  return item.code.replace(/^[A-Z]+_/, "").replaceAll("_", " ");
}
