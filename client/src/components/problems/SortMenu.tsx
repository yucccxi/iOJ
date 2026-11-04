import { ArrowUpDown, Check } from "lucide-react";
import type { CatalogSort, SortOrder } from "@/api/catalog";
import Popover from "@/components/ui/Popover";
import Segmented from "@/components/ui/Segmented";

const sortOptions: { value: CatalogSort; label: string }[] = [
  { value: "DEFAULT", label: "默认" },
  { value: "RATING", label: "难度" },
  { value: "ACCEPTED", label: "通过数" },
  { value: "SUBMISSIONS", label: "提交数" },
  { value: "ACCEPTANCE_RATE", label: "通过率" },
];

const orderOptions: { value: SortOrder; label: string }[] = [
  { value: "DESC", label: "降序" },
  { value: "ASC", label: "升序" },
];

type SortMenuProps = {
  sort: CatalogSort;
  order: SortOrder;
  onChange: (change: { sort?: CatalogSort; order?: SortOrder }) => void;
  /** Ratings are only comparable within one scope, so RATING needs exactly one selected. */
  ratingAllowed: boolean;
};

/** Stays open after a pick so the order can be changed in the same visit. */
export default function SortMenu({ sort, order, onChange, ratingAllowed }: SortMenuProps) {
  const active = sort !== "DEFAULT" || order !== "DESC";
  return (
    <Popover
      label="排序"
      align="end"
      className="flex w-50 flex-col gap-0.5 p-1.5"
      trigger={(props) => (
        <button
          {...props}
          type="button"
          aria-label="排序"
          className={`flex size-9 shrink-0 items-center justify-center rounded-control border ${
            active
              ? "border-accent bg-accent-subtle text-accent-strong"
              : "border-line bg-surface text-fg-muted hover:bg-hover"
          }`}
        >
          <ArrowUpDown />
        </button>
      )}
    >
      <p className="pt-1.5 pb-0.5 pl-2.5 text-caption text-fg-subtle">排序依据</p>
      {sortOptions.map((option) => {
        const selected = option.value === sort;
        const disabled = option.value === "RATING" && !ratingAllowed;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={selected}
            disabled={disabled}
            title={disabled ? "先在来源中只选一个，才能按难度排序" : undefined}
            onClick={() => onChange({ sort: option.value })}
            className={`flex items-center gap-2.5 rounded-control px-2.5 py-2 text-left text-label enabled:hover:bg-hover disabled:cursor-not-allowed disabled:text-fg-subtle ${
              selected ? "text-accent-strong" : "text-fg"
            }`}
          >
            <span className="flex-1">{option.label}</span>
            {selected && <Check size={14} />}
          </button>
        );
      })}
      <div className="p-1">
        <div className="h-px bg-line" />
      </div>
      <div className="flex items-center justify-between px-2.5 py-1.5">
        <span className="text-caption text-fg-muted">顺序</span>
        <Segmented
          options={orderOptions}
          value={order}
          onChange={(next) => onChange({ order: next })}
          label="顺序"
        />
      </div>
    </Popover>
  );
}
