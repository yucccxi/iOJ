import { useQueries } from "@tanstack/react-query";
import { catalogTotalQuery } from "@/api/catalog";
import type { Difficulty } from "@/api/types";
import Card from "@/components/ui/Card";
import DifficultyBadge from "@/components/ui/DifficultyBadge";
import { formatCount } from "@/lib/format";

const levels: { level: Difficulty; bar: string }[] = [
  { level: "EASY", bar: "bg-difficulty-easy" },
  { level: "MEDIUM", bar: "bg-difficulty-medium" },
  { level: "HARD", bar: "bg-difficulty-hard" },
  { level: "UNKNOWN", bar: "bg-difficulty-unknown" },
];

/** Bars are scaled to the largest level. */
export default function DifficultyCard() {
  const totals = useQueries({
    queries: levels.map(({ level }) => catalogTotalQuery({ difficulty: [level] })),
  }).map((result) => result.data);
  const max = Math.max(1, ...totals.map((total) => total ?? 0));

  return (
    <Card
      title="难度分布"
      meta={<span className="text-caption text-fg-subtle">全部来源</span>}
    >
      <div className="flex flex-col gap-card-row-gap">
        {levels.map(({ level, bar }, i) => (
          <div key={level} className="flex items-center gap-3">
            <div className="flex w-16 shrink-0">
              <DifficultyBadge level={level} />
            </div>
            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-layer-inner">
              <div
                className={`h-full rounded-full ${bar}`}
                style={{ width: `${((totals[i] ?? 0) / max) * 100}%` }}
              />
            </div>
            <span className="w-14 text-right font-mono text-mono-label text-fg-muted">
              {totals[i] === undefined ? "—" : formatCount(totals[i])}
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}
