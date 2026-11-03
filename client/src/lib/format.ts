const integer = new Intl.NumberFormat("zh-CN");

/** 33005 → "33,005". */
export function formatCount(value: number) {
  return integer.format(value);
}

/** 3200 → "3.2k"; values under 1000 stay as they are. */
export function formatCompact(value: number) {
  return value < 1000 ? String(value) : `${(value / 1000).toFixed(1)}k`;
}

/** Accepted share in [0, 1], or null when nothing has been submitted. */
export function acceptanceRate(accepted: number, submitted: number) {
  return submitted > 0 ? accepted / submitted : null;
}
