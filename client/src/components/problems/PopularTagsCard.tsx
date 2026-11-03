import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import { tagsQuery } from "@/api/catalog";
import Card from "@/components/ui/Card";
import Tag from "@/components/ui/Tag";
import { formatCompact } from "@/lib/format";

const SHOWN = 9;

/** The most used tags with their problem counts. */
export default function PopularTagsCard() {
  const { data: tags } = useQuery({
    ...tagsQuery,
    select: (all) =>
      all
        .filter((tag) => tag.problemCount > 0)
        .sort((a, b) => b.problemCount - a.problemCount)
        .slice(0, SHOWN),
  });

  return (
    <Card
      title="热门标签"
      action={
        // Opens the tag filter once the catalog supports it.
        <button
          type="button"
          disabled
          aria-label="全部标签"
          title="筛选功能即将开放"
          className="flex size-7 items-center justify-center rounded-control text-fg-subtle disabled:cursor-not-allowed"
        >
          <ArrowRight />
        </button>
      }
    >
      {tags && tags.length === 0 ? (
        <p className="text-meta text-fg-subtle">还没有题目使用标签</p>
      ) : (
        <div className="flex flex-wrap gap-2">
          {tags?.map((tag) => (
            <Tag key={tag.id} label={`${tag.name} ${formatCompact(tag.problemCount)}`} />
          ))}
        </div>
      )}
    </Card>
  );
}
