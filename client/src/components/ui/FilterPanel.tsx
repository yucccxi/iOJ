import type { ReactNode } from "react";
import Checkbox from "@/components/ui/Checkbox";
import { formatCount } from "@/lib/format";

type FilterHeaderProps = {
  title: string;
  /** Shows a 清除 action on the right. */
  onClear?: () => void;
  className?: string;
};

/** Panel title, with an optional clear action on the right. */
export function FilterHeader({
  title,
  onClear,
  className = "",
}: FilterHeaderProps) {
  return (
    <div className={`flex items-center justify-between ${className}`}>
      <span className="text-field font-bold text-fg">{title}</span>
      {onClear && (
        <button
          type="button"
          onClick={onClear}
          className="text-caption text-accent-strong hover:underline"
        >
          清除
        </button>
      )}
    </div>
  );
}

export function FilterGroupLabel({ children }: { children: ReactNode }) {
  return (
    <p className="pt-1.5 pb-0.5 pl-2 text-caption text-fg-subtle">{children}</p>
  );
}

type FilterOptionProps = {
  checked: boolean;
  onChange: () => void;
  /** Problems behind this option; undefined while loading. */
  count: number | undefined;
  children: ReactNode;
};

/** A checkbox row: the option on the left, its problem count on the right. */
export function FilterOption({
  checked,
  onChange,
  count,
  children,
}: FilterOptionProps) {
  return (
    <label
      className="flex cursor-pointer items-center gap-2.5 rounded-control p-2
        hover:bg-hover"
    >
      <Checkbox checked={checked} onChange={onChange} />
      <span className="flex min-w-0 flex-1 items-center gap-2.5">
        {children}
      </span>
      <span className="shrink-0 font-mono text-mono-label text-fg-subtle">
        {count === undefined ? "—" : formatCount(count)}
      </span>
    </label>
  );
}

type FilterSummaryProps = {
  selected: number;
  /** Problems across the selected options, or across all of them when none is selected. */
  total: number;
};

export function FilterSummary({ selected, total }: FilterSummaryProps) {
  return (
    <p className="px-2 pb-1 text-caption text-fg-subtle">
      {selected > 0 && `已选 ${selected} 项 · `}共 {formatCount(total)} 道题目
    </p>
  );
}
