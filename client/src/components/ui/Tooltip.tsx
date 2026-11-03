import type { ReactNode } from "react";
import arrowUrl from "@/assets/tooltip-arrow.svg";

type TooltipProps = {
  label: ReactNode;
  children: ReactNode;
  className?: string;
  /** Text style of the bubble. */
  labelClassName?: string;
};

/** Shows `label` above `children` on hover. */
export default function Tooltip({
  label,
  children,
  className = "",
  labelClassName = "text-number",
}: TooltipProps) {
  return (
    <span className={`group/tooltip relative ${className}`}>
      {children}
      <span
        role="tooltip"
        className="pointer-events-none invisible absolute bottom-full left-1/2 z-30 mb-1 flex -translate-x-1/2 flex-col items-center opacity-0 transition-opacity group-hover/tooltip:visible group-hover/tooltip:opacity-100"
      >
        <span className={`rounded-md bg-fg px-2 py-1 whitespace-nowrap text-fg-inverse ${labelClassName}`}>
          {label}
        </span>
        {/* The arrow file has a fixed fill; masking it lets it follow bg-fg in dark mode. */}
        <span
          className="h-1.25 w-2.5 bg-fg"
          style={{ maskImage: `url("${arrowUrl}")`, maskSize: "100% 100%" }}
        />
      </span>
    </span>
  );
}
