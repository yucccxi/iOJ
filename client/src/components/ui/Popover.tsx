import { useEffect, useRef, useState, type ReactNode, type Ref } from "react";

type TriggerProps = {
  ref: Ref<HTMLButtonElement>;
  onClick: () => void;
  "aria-expanded": boolean;
  "aria-haspopup": PopoverRole;
};

type PopoverRole = "dialog" | "menu";

type PopoverProps = {
  /** Spread the props onto the button that opens the panel. */
  trigger: (props: TriggerProps) => ReactNode;
  /** Rendered only while open, so state inside starts fresh each time. */
  children: ReactNode | ((close: () => void) => ReactNode);
  /** Accessible name of the panel. */
  label: string;
  /** `menu` for a list of links or commands; the trigger announces the same. */
  role?: PopoverRole;
  /**
   * `start` aligns with the trigger's left edge. `end` aligns with the right edge of the
   * nearest positioned ancestor instead, so a wide panel stays inside its container.
   */
  align?: "start" | "end";
  /** Size and layout of the panel. */
  className?: string;
};

/** A panel below its trigger; closes on Escape or a click outside. */
export default function Popover({
  trigger,
  children,
  label,
  role = "dialog",
  align = "start",
  className = "",
}: PopoverProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
        triggerRef.current?.focus();
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  const close = () => setOpen(false);

  return (
    <div ref={rootRef} className={align === "start" ? "relative" : ""}>
      {trigger({
        ref: triggerRef,
        onClick: () => setOpen((value) => !value),
        "aria-expanded": open,
        "aria-haspopup": role,
      })}
      {open && (
        <div
          role={role}
          aria-label={label}
          className={`absolute top-full z-20 mt-1.5 rounded-popover border border-line-popover bg-popover shadow-popover glass:backdrop-blur-layer ${align === "start" ? "left-0" : "right-0"} ${className}`}
        >
          {typeof children === "function" ? children(close) : children}
        </div>
      )}
    </div>
  );
}
