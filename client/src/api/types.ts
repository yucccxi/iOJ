// Hand-written from docs/api/frontend until the backend publishes an OpenAPI schema.

export type Page<T> = { items: T[]; total: number; page: number; size: number };

/** UNKNOWN only appears on external problems without a rating. */
export const difficulties = ["EASY", "MEDIUM", "HARD", "UNKNOWN"] as const;
export type Difficulty = (typeof difficulties)[number];

export type TagRef = { id: number; name: string };

export type Tag = TagRef & { problemCount: number };

/** `LOCAL` is reserved for problems uploaded here; the others are external platforms. */
export type Scope = {
  id: number;
  name: string;
  problemCount: number;
  createdAt: string;
};

export type CatalogItem = {
  /** Row id in the local or the external table, so only unique together with the scope. */
  id: number;
  title: string;
  /** The platform's own number, e.g. `CF_2278G`; null for local problems and when it can't be derived. */
  code: string | null;
  /** External problems derive it from their rating. */
  difficulty: Difficulty;
  /** Contest time in UTC+8 without a zone suffix; only external problems may have one. */
  time: string | null;
  /** 0 when unrated. Scales differ by scope, so values aren't comparable across scopes. */
  rating: number;
  scopeId: number;
  /** `LOCAL` for problems uploaded here. */
  scopeName: string;
  /** Only external problems have one. */
  url: string | null;
  submitCount: number;
  acceptedCount: number;
  authorId: number | null;
  authorNickname: string | null;
  tags: TagRef[];
  createdAt: string;
};
