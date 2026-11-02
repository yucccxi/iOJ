import { Link, useLocation } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import {
  Check,
  ChevronDown,
  History,
  House,
  Info,
  Library,
  MessageSquare,
} from "lucide-react";

const mainSections = [
  { to: "/", label: "首页", icon: House },
  { to: "/problems", label: "题库", icon: Library },
  { to: "/discuss", label: "讨论", icon: MessageSquare },
  { to: "/submissions", label: "提交记录", icon: History },
] as const;

const aboutSection = { to: "/about", label: "关于", icon: Info } as const;

type Section = (typeof mainSections)[number] | typeof aboutSection;

function findSection(pathname: string): Section {
  const match = [...mainSections, aboutSection].find((section) =>
    section.to === "/"
      ? pathname === "/"
      : pathname === section.to || pathname.startsWith(`${section.to}/`),
  );
  // Pages outside every section (login, register) fall back to home.
  return match ?? mainSections[0];
}

export default function SectionSwitcher() {
  const pathname = useLocation({ select: (location) => location.pathname });
  const current = findSection(pathname);
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  function renderItem(section: Section) {
    const isCurrent = section === current;
    const SectionIcon = section.icon;
    return (
      <Link
        key={section.to}
        to={section.to}
        role="menuitem"
        activeOptions={{ exact: section.to === "/" }}
        onClick={() => setOpen(false)}
        className={`flex items-center gap-2.5 rounded-control px-2.5 py-2 text-label hover:bg-hover ${
          isCurrent ? "text-accent-strong" : "text-fg"
        }`}
      >
        <SectionIcon size={16} absoluteStrokeWidth />
        <span className="flex-1">{section.label}</span>
        {isCurrent && <Check size={14} absoluteStrokeWidth />}
      </Link>
    );
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="flex items-center gap-1.5 rounded-control bg-selected px-2.5 py-1.5 text-field text-accent-strong"
      >
        <current.icon size={16} absoluteStrokeWidth />
        {current.label}
        <ChevronDown size={14} absoluteStrokeWidth />
      </button>

      {open && (
        <div
          role="menu"
          className="absolute top-full left-0 z-20 mt-2 flex w-50 flex-col gap-0.5 rounded-popover border border-line-popover bg-popover p-1.5 shadow-popover backdrop-blur-layer"
        >
          {mainSections.map(renderItem)}
          <div className="p-1">
            <div className="h-px bg-line" />
          </div>
          {renderItem(aboutSection)}
        </div>
      )}
    </div>
  );
}
