import type { ReactNode } from "react";
import { Link } from "@tanstack/react-router";
import { ExternalLink } from "lucide-react";
import type { CatalogItem } from "@/api/types";
import SourceCell from "@/components/problems/SourceCell";
import DifficultyBadge from "@/components/ui/DifficultyBadge";
import RateBar from "@/components/ui/RateBar";
import Tag from "@/components/ui/Tag";
import Tooltip from "@/components/ui/Tooltip";
import { problemCode } from "@/lib/problem";
import { ratingColor } from "@/lib/rating";

// Widths include the 16px column gap (12px row inset on the outer edges).
const columns = [
  { label: "题号", className: "w-24" },
  { label: "题目", className: "" },
  { label: "标签", className: "w-44" },
  { label: "难度", className: "w-22.75" },
  { label: "通过率", className: "w-35" },
  { label: "来源", className: "w-37" },
];
const cell = "pl-4 first:pl-3 last:pr-3";

// Makes the whole row the link's hit area while other cells keep their own hover.
const stretchedLink =
  "truncate text-field text-fg group-hover/row:text-accent-strong after:absolute after:inset-0 focus-visible:outline-none focus-visible:after:rounded-control focus-visible:after:ring-2 focus-visible:after:ring-accent-ring";

function TitleCell({ item }: { item: CatalogItem }) {
  if (item.url) {
    return (
      <div className="flex min-w-0 items-center gap-1.5">
        <a
          href={item.url}
          target="_blank"
          rel="noreferrer"
          className={stretchedLink}
        >
          {item.title}
        </a>
        <ExternalLink
          size={13}
          aria-label="外部链接"
          className="shrink-0 text-fg-subtle"
        />
      </div>
    );
  }
  return (
    <Link
      to="/problems/$problemId"
      params={{ problemId: String(item.id) }}
      className={`block ${stretchedLink}`}
    >
      {item.title}
    </Link>
  );
}

function DifficultyCell({ item }: { item: CatalogItem }) {
  const badge = <DifficultyBadge level={item.difficulty} />;
  if (item.difficulty === "UNKNOWN" || item.rating < 0) return badge;
  return (
    // Above the row's stretched link, so hovering shows the rating.
    <Tooltip
      label={
        <span
          className={`font-mono ${ratingColor(item.scopeName, item.rating)}`}
        >
          {item.rating}
        </span>
      }
      className="z-10 cursor-default"
    >
      {badge}
    </Tooltip>
  );
}

type ProblemTableProps = {
  items: CatalogItem[] | undefined;
  /** Rows to sketch while the first page loads. */
  skeletonRows: number;
  /** Shown in place of rows; takes priority over items. */
  message?: ReactNode;
};

export default function ProblemTable({
  items,
  skeletonRows,
  message,
}: ProblemTableProps) {
  return (
    <table className="w-full table-fixed border-separate border-spacing-0">
      <colgroup>
        {columns.map((column) => (
          <col key={column.label} className={column.className} />
        ))}
      </colgroup>
      <thead>
        <tr className="text-left text-caption text-fg-subtle">
          {columns.map((column) => (
            <th
              key={column.label}
              scope="col"
              className={`bg-layer-inner py-2 font-normal first:rounded-l-control last:rounded-r-control ${cell}`}
            >
              {column.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {message ? (
          <tr>
            <td
              colSpan={columns.length}
              className="py-12 text-center text-meta text-fg-muted"
            >
              {message}
            </td>
          </tr>
        ) : items ? (
          items.map((item) => (
            <tr
              key={`${item.scopeId}-${item.id}`}
              className="group/row relative hover:bg-hover"
            >
              <td
                title={item.code ?? undefined}
                className={`truncate border-b border-line py-2.5 font-mono text-mono-label text-fg-muted ${cell}`}
              >
                {problemCode(item)}
              </td>
              <td className={`border-b border-line py-2.5 ${cell}`}>
                <TitleCell item={item} />
              </td>
              <td className={`border-b border-line py-2.5 ${cell}`}>
                {/* One row tall: chips that don't fit wrap onto a hidden second row instead of being cut. */}
                <div className="flex h-6.5 flex-wrap gap-1.5 overflow-hidden">
                  {item.tags.map((tag) => (
                    <Tag key={tag.id} label={tag.name} />
                  ))}
                </div>
              </td>
              <td className={`border-b border-line py-2.5 ${cell}`}>
                <DifficultyCell item={item} />
              </td>
              <td className={`border-b border-line py-2.5 ${cell}`}>
                <RateBar
                  accepted={item.acceptedCount}
                  submitted={item.submitCount}
                />
              </td>
              <td className={`border-b border-line py-2.5 ${cell}`}>
                <SourceCell item={item} />
              </td>
            </tr>
          ))
        ) : (
          Array.from({ length: skeletonRows }, (_, i) => (
            <tr key={i}>
              <td
                colSpan={columns.length}
                className="border-b border-line px-3 py-2.5"
              >
                <div className="h-6.5 animate-pulse rounded-control bg-layer-inner" />
              </td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}
