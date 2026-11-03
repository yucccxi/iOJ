// Hand-written from docs/shared/api until the backend publishes an OpenAPI schema.

export type Page<T> = { items: T[]; total: number; page: number; size: number };

export type Difficulty = "EASY" | "MEDIUM" | "HARD";

export type TagRef = { id: number; name: string };

export type Tag = TagRef & { problemCount: number };

export type CatalogItem = {
  kind: "LOCAL" | "EXTERNAL";
  id: number;
  title: string;
  /** Only LOCAL problems have one. */
  difficulty: Difficulty | null;
  rating: number | null;
  sourceId: number | null;
  sourceName: string | null;
  /** Only EXTERNAL problems have one. */
  url: string | null;
  submitCount: number;
  acceptedCount: number;
  authorId: number | null;
  authorNickname: string | null;
  tags: TagRef[];
  createdAt: string;
};
