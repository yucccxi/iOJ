import { queryOptions } from "@tanstack/react-query";
import { get } from "@/api/client";
import type { CatalogItem, Difficulty, Page, Scope, Tag } from "@/api/types";

export const catalogSorts = [
  "DEFAULT",
  "RATING",
  "ACCEPTED",
  "SUBMISSIONS",
  "ACCEPTANCE_RATE",
] as const;
export type CatalogSort = (typeof catalogSorts)[number];
export type TagMatch = "ANY" | "ALL";
export type SortOrder = "ASC" | "DESC";

/** Name of the reserved scope that every problem uploaded here belongs to. */
export const LOCAL_SCOPE = "LOCAL";

/** Array values are sent as repeated params and match any of them. */
export type CatalogParams = {
  keyword?: string;
  difficulty?: Difficulty[];
  tagId?: number[];
  /** Whether several `tagId`s match ANY (the default) or ALL. */
  tagMatch?: TagMatch;
  rating?: number;
  minRating?: number;
  maxRating?: number;
  /** Ids from `/meta/scopes`; an unknown id is a 400, not an empty page. */
  scopeId?: number[];
  /** RATING needs exactly one `scopeId`; ratings from different scopes aren't comparable. */
  sort?: CatalogSort;
  order?: SortOrder;
  page?: number;
  size?: number;
};

export function catalogQuery(params: CatalogParams) {
  return queryOptions({
    queryKey: ["catalog", params],
    queryFn: ({ signal }) => get<Page<CatalogItem>>("/catalog", params, signal),
  });
}

/**
 * How many problems match, read from a one-item page since there is no stats endpoint.
 * Shares its cache entry with `catalogQuery({ ...params, size: 1 })`.
 */
export function catalogTotalQuery(
  params: Omit<CatalogParams, "page" | "size">,
) {
  const pageParams = { ...params, size: 1 };
  return queryOptions({
    queryKey: ["catalog", pageParams],
    queryFn: ({ signal }) =>
      get<Page<CatalogItem>>("/catalog", pageParams, signal),
    select: (page) => page.total,
  });
}

export const tagsQuery = queryOptions({
  queryKey: ["meta", "tags"],
  queryFn: ({ signal }) => get<Tag[]>("/meta/tags", {}, signal),
  staleTime: 5 * 60_000,
});

export const scopesQuery = queryOptions({
  queryKey: ["meta", "scopes"],
  queryFn: ({ signal }) => get<Scope[]>("/meta/scopes", {}, signal),
  staleTime: 5 * 60_000,
});

/** The local scope, found by name because its id differs between databases. */
export const localScopeQuery = queryOptions({
  ...scopesQuery,
  select: (scopes) => scopes.find((scope) => scope.name === LOCAL_SCOPE),
});
