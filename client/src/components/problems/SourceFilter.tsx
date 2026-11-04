import { useQuery } from "@tanstack/react-query";
import { catalogTotalQuery, LOCAL_SCOPE, sourcesQuery } from "@/api/catalog";
import PlatformMark from "@/components/problems/PlatformMark";
import {
  FilterGroupLabel,
  FilterHeader,
  FilterOption,
  FilterSummary,
} from "@/components/ui/FilterPanel";
import Popover from "@/components/ui/Popover";
import SelectButton from "@/components/ui/SelectButton";

type SourceFilterProps = {
  /** Selected `scope` values. */
  value: string[];
  onChange: (value: string[]) => void;
};

/** Problems uploaded here, then one option per source from `/meta/sources`. */
export default function SourceFilter({ value, onChange }: SourceFilterProps) {
  const { data: sources } = useQuery(sourcesQuery);
  const { data: localTotal } = useQuery(catalogTotalQuery({ scope: [LOCAL_SCOPE] }));

  const counts = new Map<string, number>([
    [LOCAL_SCOPE, localTotal ?? 0],
    ...(sources ?? []).map((source) => [source.name, source.problemCount] as const),
  ]);
  const counted = value.length > 0 ? value : [...counts.keys()];
  const total = counted.reduce((sum, scope) => sum + (counts.get(scope) ?? 0), 0);

  function toggle(scope: string) {
    onChange(value.includes(scope) ? value.filter((v) => v !== scope) : [...value, scope]);
  }

  return (
    <Popover
      label="按来源筛选"
      className="flex w-70 flex-col gap-1 p-2"
      trigger={(props) => (
        <SelectButton
          {...props}
          label={value.length > 0 ? `来源 · ${value.length}` : "来源"}
          active={value.length > 0}
        />
      )}
    >
      <FilterHeader title="来源" onClear={() => onChange([])} className="px-2 pt-1 pb-1.5" />

      <FilterGroupLabel>本站</FilterGroupLabel>
      <FilterOption
        checked={value.includes(LOCAL_SCOPE)}
        onChange={() => toggle(LOCAL_SCOPE)}
        count={localTotal}
      >
        <span className="flex size-5 shrink-0 items-center justify-center rounded-sm bg-accent text-tag text-accent-on">
          站
        </span>
        <span className="truncate text-label text-fg">用户上传</span>
      </FilterOption>

      <FilterGroupLabel>外部平台</FilterGroupLabel>
      {sources?.map((source) => (
        <FilterOption
          key={source.id}
          checked={value.includes(source.name)}
          onChange={() => toggle(source.name)}
          count={source.problemCount}
        >
          <PlatformMark sourceName={source.name} className="size-5" />
          <span className="truncate text-label text-fg">{source.name}</span>
        </FilterOption>
      ))}

      <div className="py-1">
        <div className="h-px bg-line" />
      </div>
      <FilterSummary selected={value.length} total={total} />
    </Popover>
  );
}
