import { queryOptions } from "@tanstack/react-query";
import { get } from "@/api/client";
import type { CatalogItem, Difficulty, Page, Tag } from "@/api/types";

export type CatalogParams = {
  keyword?: string;
  kind?: CatalogItem["kind"];
  difficulty?: Difficulty;
  page?: number;
  size?: number;
};

export function catalogQuery(params: CatalogParams) {
  return queryOptions({
    queryKey: ["catalog", params],
    queryFn: ({ signal }) => get<Page<CatalogItem>>("/catalog", params, signal),
  });
}

export const tagsQuery = queryOptions({
  queryKey: ["meta", "tags"],
  queryFn: ({ signal }) => get<Tag[]>("/meta/tags", {}, signal),
  staleTime: 5 * 60_000,
});
