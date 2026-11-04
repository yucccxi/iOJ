import { LOCAL_SCOPE } from "@/api/catalog";
import type { CatalogItem } from "@/api/types";

/** Uploaded here rather than linked from another platform. */
export function isLocal(item: CatalogItem) {
  return item.scopeName === LOCAL_SCOPE;
}

/**
 * Display ID: `IOJ{id}` for problems uploaded here, the platform's number for external ones.
 * The platform prefix is dropped (`CF_2278G` → `2278G`, `AT_ABC482_G` → `ABC482 G`)
 * because the 来源 column already names the platform.
 */
export function problemCode(item: CatalogItem) {
  if (isLocal(item)) return `IOJ${item.id}`;
  if (!item.code) return "—";
  return item.code.replace(/^[A-Z]+_/, "").replaceAll("_", " ");
}
