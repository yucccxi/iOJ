import type { CatalogItem } from "@/api/types";
import PlatformMark from "@/components/problems/PlatformMark";
import Avatar from "@/components/ui/Avatar";

/** The platform for external problems, the uploader for ours. */
export default function SourceCell({ item }: { item: CatalogItem }) {
  const name = item.kind === "LOCAL" ? item.authorNickname : item.sourceName;
  if (!name) return <span className="font-mono text-badge text-fg-subtle">—</span>;

  return (
    <span className="flex min-w-0 items-center gap-1.5">
      {item.kind === "EXTERNAL" ? (
        <PlatformMark
          sourceName={name}
          className="size-3"
          fallback={<Avatar name={name} size="xs" />}
        />
      ) : (
        <Avatar name={name} size="xs" />
      )}
      <span className="truncate font-mono text-badge text-fg-muted">{name}</span>
    </span>
  );
}
