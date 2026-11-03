import type { CatalogItem } from "@/api/types";
import Avatar from "@/components/ui/Avatar";
import atcoderMark from "@/assets/platforms/atcoder.png";
import codeforcesMark from "@/assets/platforms/codeforces.png";
import leetcodeMark from "@/assets/platforms/leetcode.png";
import luoguMark from "@/assets/platforms/luogu.png";

// Keyed by lower-cased source name, as admins may type either spelling.
const platformMarks: Record<string, string> = {
  洛谷: luoguMark,
  luogu: luoguMark,
  codeforces: codeforcesMark,
  leetcode: leetcodeMark,
  力扣: leetcodeMark,
  atcoder: atcoderMark,
};

/** The platform for external problems, the uploader for ours. */
export default function SourceCell({ item }: { item: CatalogItem }) {
  const name = item.kind === "LOCAL" ? item.authorNickname : item.sourceName;
  if (!name) return <span className="font-mono text-badge text-fg-subtle">—</span>;

  const mark = item.kind === "EXTERNAL" ? platformMarks[name.toLowerCase()] : undefined;
  return (
    <span className="flex min-w-0 items-center gap-1.5">
      {mark ? (
        // Luogu's mark has a white background. Multiply hides it on light pages only;
        // dark themes need a transparent version of the file.
        <img
          src={mark}
          alt=""
          className={`size-3 shrink-0 rounded-sm object-contain ${mark === luoguMark ? "mix-blend-multiply" : ""}`}
        />
      ) : (
        <Avatar name={name} size="xs" />
      )}
      <span className="truncate font-mono text-badge text-fg-muted">{name}</span>
    </span>
  );
}
