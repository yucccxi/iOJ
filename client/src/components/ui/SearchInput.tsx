import { useEffect, useRef } from "react";
import { Search } from "lucide-react";

type SearchInputProps = {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  className?: string;
};

/** Pressing "/" anywhere outside a text field focuses it. */
export default function SearchInput({ value, onChange, placeholder, className = "" }: SearchInputProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== "/" || event.metaKey || event.ctrlKey || event.altKey) return;
      const target = event.target as HTMLElement;
      if (target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName)) return;
      event.preventDefault();
      inputRef.current?.focus();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, []);

  return (
    <label
      className={`flex h-9 items-center gap-2 rounded-control border border-line bg-layer-inner px-3 focus-within:border-accent focus-within:ring-3 focus-within:ring-accent-ring ${className}`}
    >
      <Search className="text-fg-subtle" />
      <input
        ref={inputRef}
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="min-w-0 flex-1 bg-transparent text-label text-fg outline-none placeholder:text-fg-subtle [&::-webkit-search-cancel-button]:hidden"
      />
      <kbd className="rounded-sm border border-line bg-surface px-1.5 py-px font-mono text-mono-meta text-fg-muted">
        /
      </kbd>
    </label>
  );
}
