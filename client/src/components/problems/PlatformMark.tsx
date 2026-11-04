import type { ReactNode } from "react";
import atcoderMark from "@/assets/platforms/atcoder.png";
import codeforcesMark from "@/assets/platforms/codeforces.png";
import leetcodeMark from "@/assets/platforms/leetcode.png";
import luoguMark from "@/assets/platforms/luogu.png";

// Keyed by lower-cased scope name, as admins may type either spelling.
const platformMarks: Record<string, string> = {
  洛谷: luoguMark,
  luogu: luoguMark,
  codeforces: codeforcesMark,
  leetcode: leetcodeMark,
  力扣: leetcodeMark,
  atcoder: atcoderMark,
};

type PlatformMarkProps = {
  scopeName: string;
  className: string;
  /** Shown for scopes without a known logo. */
  fallback?: ReactNode;
};

/** Logo of a known external platform. */
export default function PlatformMark({
  scopeName,
  className,
  fallback = null,
}: PlatformMarkProps) {
  const mark = platformMarks[scopeName.toLowerCase()];
  if (!mark) return fallback;
  return (
    // Luogu's mark has a white background. Multiply hides it on light pages only;
    // dark themes need a transparent version of the file.
    <img
      src={mark}
      alt=""
      className={`shrink-0 rounded-sm object-contain
        ${mark === luoguMark ? "mix-blend-multiply" : ""} ${className}`}
    />
  );
}
