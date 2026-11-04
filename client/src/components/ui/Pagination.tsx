import { formatCount } from "@/lib/format";

type PaginationProps = {
  page: number;
  size: number;
  total: number;
  /** Counted thing in the summary, e.g. "题". */
  unit: string;
  onPageChange: (page: number) => void;
};

/** Pages to show: all of them when there are few, otherwise both ends and the current page's neighbours. */
function pageItems(current: number, count: number): (number | "gap")[] {
  if (count <= 7) return Array.from({ length: count }, (_, i) => i + 1);
  const pages = new Set([1, 2, current - 1, current, current + 1, count]);
  if (current <= 3) pages.add(3);
  const sorted = [...pages]
    .filter((p) => p >= 1 && p <= count)
    .sort((a, b) => a - b);
  return sorted.flatMap((p, i) =>
    i > 0 && p - sorted[i - 1] > 1 ? ["gap" as const, p] : [p],
  );
}

const buttonClass =
  "flex h-8 min-w-8 items-center justify-center rounded-control px-2.5 font-mono text-mono-label";

/** Range summary on the left, page buttons on the right. */
export default function Pagination({
  page,
  size,
  total,
  unit,
  onPageChange,
}: PaginationProps) {
  const count = Math.max(1, Math.ceil(total / size));
  const first = total === 0 ? 0 : (page - 1) * size + 1;
  const last = Math.min(page * size, total);

  return (
    <nav aria-label="分页" className="flex items-center justify-between">
      <p className="text-caption text-fg-subtle">
        {total === 0
          ? `共 0 ${unit}`
          : `第 ${formatCount(first)}–${formatCount(last)} ${unit}，共 ${formatCount(total)} ${unit}`}
      </p>
      <div className="flex items-center gap-1.5">
        <button
          type="button"
          aria-label="上一页"
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
          className={`${buttonClass} border border-line text-fg-muted
            enabled:hover:bg-hover disabled:cursor-not-allowed
            disabled:text-fg-subtle`}
        >
          ‹
        </button>
        {pageItems(page, count).map((item, i) =>
          item === "gap" ? (
            <span
              key={`gap-${i}`}
              className="self-end font-mono text-mono-label text-fg-subtle"
            >
              ...
            </span>
          ) : (
            <button
              key={item}
              type="button"
              aria-current={item === page ? "page" : undefined}
              onClick={() => onPageChange(item)}
              className={`${buttonClass} ${
                item === page
                  ? "bg-accent-subtle text-accent-strong"
                  : "border border-line text-fg-muted hover:bg-hover"
                }`}
            >
              {item}
            </button>
          ),
        )}
        <button
          type="button"
          aria-label="下一页"
          disabled={page >= count}
          onClick={() => onPageChange(page + 1)}
          className={`${buttonClass} border border-line text-fg-muted
            enabled:hover:bg-hover disabled:cursor-not-allowed
            disabled:text-fg-subtle`}
        >
          ›
        </button>
      </div>
    </nav>
  );
}
