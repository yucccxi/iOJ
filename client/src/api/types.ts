// Hand-written from docs/shared/api until the backend publishes an OpenAPI schema.

export type Page<T> = { items: T[]; total: number; page: number; size: number };

/** UNKNOWN only appears on external problems whose source has no rating. */
export const difficulties = ["EASY", "MEDIUM", "HARD", "UNKNOWN"] as const;
export type Difficulty = (typeof difficulties)[number];

export type TagRef = { id: number; name: string };

export type Tag = TagRef & { problemCount: number };

export type Source = { id: number; name: string; problemCount: number; createdAt: string };

export type CatalogItem = {
  kind: "LOCAL" | "EXTERNAL";
  id: number;
  title: string;
  /** The platform's own number, e.g. `CF_2278G`; null for LOCAL and when it can't be derived. */
  code: string | null;
  /** External problems derive it from their source rating. */
  difficulty: Difficulty;
  /** Contest time in UTC+8 without a zone suffix; only EXTERNAL problems may have one. */
  time: string | null;
  /** 0 when unrated. Scales differ by source, so values aren't comparable across sources. */
  rating: number;
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
