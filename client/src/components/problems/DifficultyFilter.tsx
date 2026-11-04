import { useQueries } from "@tanstack/react-query";
import { catalogTotalQuery } from "@/api/catalog";
import { difficulties, type Difficulty } from "@/api/types";
import DifficultyBadge from "@/components/ui/DifficultyBadge";
import { FilterHeader, FilterOption, FilterSummary } from "@/components/ui/FilterPanel";
import Popover from "@/components/ui/Popover";
import SelectButton from "@/components/ui/SelectButton";

type DifficultyFilterProps = {
  value: Difficulty[];
  onChange: (value: Difficulty[]) => void;
};

/** Counts are across all scopes and ignore the other filters. */
export default function DifficultyFilter({ value, onChange }: DifficultyFilterProps) {
  const totals = useQueries({
    queries: difficulties.map((level) => catalogTotalQuery({ difficulty: [level] })),
  }).map((result) => result.data);

  const counted: readonly Difficulty[] = value.length > 0 ? value : difficulties;
  const total = counted.reduce(
    (sum, level) => sum + (totals[difficulties.indexOf(level)] ?? 0),
    0,
  );

  function toggle(level: Difficulty) {
    onChange(value.includes(level) ? value.filter((v) => v !== level) : [...value, level]);
  }

  return (
    <Popover
      label="按难度筛选"
      className="flex w-55 flex-col gap-2.5 p-2"
      trigger={(props) => (
        <SelectButton
          {...props}
          label={value.length > 0 ? `难度 · ${value.length}` : "难度"}
          active={value.length > 0}
        />
      )}
    >
      <FilterHeader title="难度" onClear={() => onChange([])} className="px-2 pt-1" />
      {difficulties.map((level, i) => (
        <FilterOption
          key={level}
          checked={value.includes(level)}
          onChange={() => toggle(level)}
          count={totals[i]}
        >
          <DifficultyBadge level={level} />
        </FilterOption>
      ))}
      <div className="h-px bg-line" />
      <FilterSummary selected={value.length} total={total} />
    </Popover>
  );
}
