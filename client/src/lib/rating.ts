// Upper bounds are exclusive; the last tier has no bound.
type Tier = [below: number, className: string];

const codeforces: Tier[] = [
  [1200, "text-rating-gray"],
  [1400, "text-rating-green"],
  [1600, "text-rating-cyan"],
  [1900, "text-rating-blue"],
  [2100, "text-rating-violet"],
  [2400, "text-rating-orange"],
  [Infinity, "text-rating-red"],
];

// AtCoder Problems' difficulty colors, which follow AtCoder's user colors.
const atcoder: Tier[] = [
  [400, "text-rating-gray"],
  [800, "text-rating-brown"],
  [1200, "text-rating-green"],
  [1600, "text-rating-cyan"],
  [2000, "text-rating-blue"],
  [2400, "text-rating-yellow"],
  [2800, "text-rating-orange"],
  [Infinity, "text-rating-red"],
];

// Keyed by lower-cased scope name, as admins may type either spelling.
const tiersByScope: Record<string, Tier[]> = { codeforces, atcoder };

/** Text color class for a rating on its platform's scale; undefined for platforms without one. */
export function ratingColor(scopeName: string, rating: number) {
  const tiers = tiersByScope[scopeName.toLowerCase()];
  return tiers?.find(([below]) => rating < below)?.[1];
}
