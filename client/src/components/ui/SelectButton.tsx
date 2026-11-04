import type { ComponentProps } from "react";
import { ChevronDown } from "lucide-react";

type SelectButtonProps = ComponentProps<"button"> & {
  label: string;
  /** A filter is applied. */
  active?: boolean;
};

/** Trigger button of a filter dropdown. */
export default function SelectButton({
  label,
  active = false,
  className = "",
  ...props
}: SelectButtonProps) {
  return (
    <button
      type="button"
      className={`flex h-9 shrink-0 items-center gap-1.5 rounded-control border px-3 text-label disabled:cursor-not-allowed ${
        active
          ? "border-accent bg-accent-subtle text-accent-strong"
          : "border-line bg-surface text-fg enabled:hover:bg-hover"
      } ${className}`}
      {...props}
    >
      {label}
      <ChevronDown size={14} className={active ? "" : "text-fg-subtle"} />
    </button>
  );
}
