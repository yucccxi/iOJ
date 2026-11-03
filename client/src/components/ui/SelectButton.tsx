import type { ComponentProps } from "react";
import { ChevronDown } from "lucide-react";

type SelectButtonProps = ComponentProps<"button"> & { label: string };

/** Trigger button of a filter dropdown. */
export default function SelectButton({ label, className = "", ...props }: SelectButtonProps) {
  return (
    <button
      type="button"
      className={`flex h-9 shrink-0 items-center gap-1.5 rounded-control border border-line bg-surface px-3 text-label text-fg enabled:hover:bg-hover disabled:cursor-not-allowed ${className}`}
      {...props}
    >
      {label}
      <ChevronDown size={14} className="text-fg-subtle" />
    </button>
  );
}
