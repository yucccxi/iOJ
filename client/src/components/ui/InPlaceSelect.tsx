import { useEffect, useRef, useState } from "react";
import { ChevronDown } from "lucide-react";

type Option<T extends string> = { value: T; label: string };

type InPlaceSelectProps<T extends string> = {
  value: T;
  options: Option<T>[];
  onChange: (value: T) => void;
  /** Accessible name of the control, e.g. "Theme". */
  label: string;
  className?: string;
};

export default function InPlaceSelect<T extends string>({
  value,
  options,
  onChange,
  label,
  className = "",
}: InPlaceSelectProps<T>) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const current =
    options.find((option) => option.value === value) ?? options[0];

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== "Escape") return;
      setOpen(false);
      triggerRef.current?.focus();
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  function choose(next: T) {
    onChange(next);
    setOpen(false);
    triggerRef.current?.focus();
  }

  return (
    <div ref={rootRef} className={`relative h-7 w-23.5 shrink-0 ${className}`}>
      <button
        ref={triggerRef}
        type="button"
        aria-label={`${label}: ${current?.label ?? ""}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="absolute inset-0 flex items-center gap-1 overflow-hidden
          rounded-control border border-line-popover bg-surface pr-2 pl-2.5
          text-caption font-medium"
      >
        <span className="flex-1 truncate text-left text-accent-strong">
          {current?.label}
        </span>
        <ChevronDown size={12} className="shrink-0 text-fg-subtle" />
      </button>

      {open && (
        <div
          role="listbox"
          aria-label={label}
          className="absolute inset-x-0 top-0 z-20 flex flex-col overflow-hidden
            rounded-control border border-line-popover bg-hover shadow-popover
            glass:backdrop-blur-layer"
        >
          {options.map((option) => {
            const selected = option.value === value;
            return (
              <button
                key={option.value}
                type="button"
                role="option"
                aria-selected={selected}
                onClick={() => choose(option.value)}
                className={`flex w-full items-center rounded-control py-1.25
                pr-2 pl-2.5 text-left text-caption font-medium ${
                  selected
                    ? "bg-surface text-accent-strong"
                    : "text-fg hover:bg-surface"
                }`}
              >
                {option.label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
