import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Check, Search, X } from "lucide-react";
import { tagsQuery, type TagMatch } from "@/api/catalog";
import Button from "@/components/ui/Button";
import { FilterHeader } from "@/components/ui/FilterPanel";
import Popover from "@/components/ui/Popover";
import Segmented from "@/components/ui/Segmented";
import SelectButton from "@/components/ui/SelectButton";

const matchOptions: { value: TagMatch; label: string }[] = [
  { value: "ANY", label: "满足任一" },
  { value: "ALL", label: "满足全部" },
];

type TagFilterProps = {
  tagId: number[];
  tagMatch: TagMatch;
  onApply: (tagId: number[], tagMatch: TagMatch) => void;
};

/** Edits a draft and only filters on 应用; closing the panel discards the draft. */
export default function TagFilter(props: TagFilterProps) {
  const count = props.tagId.length;
  return (
    <Popover
      label="按标签筛选"
      align="end"
      className="w-95"
      trigger={(trigger) => (
        <SelectButton
          {...trigger}
          label={count > 0 ? `标签 · ${count}` : "标签"}
          active={count > 0}
        />
      )}
    >
      {(close) => (
        <TagPanel
          {...props}
          onApply={(tagId, tagMatch) => {
            props.onApply(tagId, tagMatch);
            close();
          }}
        />
      )}
    </Popover>
  );
}

function TagPanel({ tagId, tagMatch, onApply }: TagFilterProps) {
  const [selected, setSelected] = useState(tagId);
  const [match, setMatch] = useState(tagMatch);
  const [query, setQuery] = useState("");
  const { data: tags } = useQuery({
    ...tagsQuery,
    select: (all) =>
      all
        .filter((tag) => tag.problemCount > 0)
        .sort((a, b) => b.problemCount - a.problemCount),
  });

  const needle = query.trim().toLowerCase();
  const shown = tags?.filter((tag) => tag.name.toLowerCase().includes(needle));

  function toggle(id: number) {
    setSelected((ids) => (ids.includes(id) ? ids.filter((v) => v !== id) : [...ids, id]));
  }

  return (
    <div className="flex flex-col gap-3 px-4 py-3">
      <FilterHeader title="标签" />

      <label className="flex h-8.5 items-center gap-2 rounded-control border border-line bg-layer-inner px-2.5 focus-within:border-accent focus-within:ring-3 focus-within:ring-accent-ring">
        <Search size={14} className="text-fg-subtle" />
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索标签…"
          aria-label="搜索标签"
          autoFocus
          className="min-w-0 flex-1 bg-transparent text-meta text-fg outline-none placeholder:text-fg-subtle [&::-webkit-search-cancel-button]:hidden"
        />
      </label>

      {/* The tag list is long; it scrolls so the actions stay in view. */}
      <div className="flex max-h-60 flex-wrap content-start gap-1.5 overflow-y-auto">
        {shown?.length === 0 && (
          <p className="text-caption text-fg-subtle">没有匹配的标签</p>
        )}
        {shown?.map((tag) => {
          const on = selected.includes(tag.id);
          return (
            <button
              key={tag.id}
              type="button"
              aria-pressed={on}
              onClick={() => toggle(tag.id)}
              className={`flex items-center gap-1 rounded-full border px-2.5 py-1 text-tag ${
                on
                  ? "border-accent bg-accent-subtle text-accent-strong"
                  : "border-line bg-surface text-fg-muted hover:bg-hover"
              }`}
            >
              {on && <Check size={12} />}
              {tag.name}
            </button>
          );
        })}
      </div>

      <div className="h-px bg-line" />

      <div className="flex items-center gap-2">
        <span className="text-caption text-fg-muted">匹配方式</span>
        <Segmented options={matchOptions} value={match} onChange={setMatch} label="匹配方式" />
      </div>

      <div className="flex items-center justify-between">
        <span className="text-caption text-fg-subtle">已选 {selected.length} 个标签</span>
        <div className="flex gap-2">
          <Button
            variant="secondary"
            onClick={() => {
              setSelected(tagId);
              setMatch(tagMatch);
            }}
          >
            <X />
            重置
          </Button>
          <Button onClick={() => onApply(selected, match)}>
            <Check />
            应用
          </Button>
        </div>
      </div>
    </div>
  );
}
