import type { Difficulty } from "@/api/types";

const levels: Record<Difficulty, { label: string; className: string }> = {
  EASY: {
    label: "简单",
    className: "bg-difficulty-easy-bg text-difficulty-easy",
  },
  MEDIUM: {
    label: "中等",
    className: "bg-difficulty-medium-bg text-difficulty-medium",
  },
  HARD: {
    label: "困难",
    className: "bg-difficulty-hard-bg text-difficulty-hard",
  },
  UNKNOWN: {
    label: "未知",
    className: "bg-difficulty-unknown-bg text-difficulty-unknown",
  },
};

export default function DifficultyBadge({ level }: { level: Difficulty }) {
  const { label, className } = levels[level];
  return (
    <span
      className={`shrink-0 rounded-full px-2 py-px text-tag whitespace-nowrap
        ${className}`}
    >
      {label}
    </span>
  );
}
