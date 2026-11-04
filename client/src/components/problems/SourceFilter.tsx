import { useQuery } from "@tanstack/react-query";
import { LOCAL_SCOPE, scopesQuery } from "@/api/catalog";
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
  /** Selected scope ids. */
  value: number[];
  onChange: (value: number[]) => void;
};

/** The local scope first, then one option per external platform from `/meta/scopes`. */
export default function SourceFilter({ value, onChange }: SourceFilterProps) {
  const { data: scopes = [] } = useQuery(scopesQuery);
  const local = scopes.find((scope) => scope.name === LOCAL_SCOPE);
  const external = scopes.filter((scope) => scope !== local);

  const counted =
    value.length > 0
      ? scopes.filter((scope) => value.includes(scope.id))
      : scopes;
  const total = counted.reduce((sum, scope) => sum + scope.problemCount, 0);

  function toggle(id: number) {
    onChange(
      value.includes(id) ? value.filter((v) => v !== id) : [...value, id],
    );
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
      <FilterHeader
        title="来源"
        onClear={() => onChange([])}
        className="px-2 pt-1 pb-1.5"
      />

      {local && (
        <>
          <FilterGroupLabel>本站</FilterGroupLabel>
          <FilterOption
            checked={value.includes(local.id)}
            onChange={() => toggle(local.id)}
            count={local.problemCount}
          >
            <span className="flex size-5 shrink-0 items-center justify-center rounded-sm bg-accent text-tag text-accent-on">
              站
            </span>
            <span className="truncate text-label text-fg">用户上传</span>
          </FilterOption>
        </>
      )}

      <FilterGroupLabel>外部平台</FilterGroupLabel>
      {external.map((scope) => (
        <FilterOption
          key={scope.id}
          checked={value.includes(scope.id)}
          onChange={() => toggle(scope.id)}
          count={scope.problemCount}
        >
          <PlatformMark scopeName={scope.name} className="size-5" />
          <span className="truncate text-label text-fg">{scope.name}</span>
        </FilterOption>
      ))}

      <div className="py-1">
        <div className="h-px bg-line" />
      </div>
      <FilterSummary selected={value.length} total={total} />
    </Popover>
  );
}
