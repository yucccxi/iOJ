import { Link, useLocation } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import Icon from "@/components/Icon";
import checkIcon from "@/assets/icons/check.svg";
import chevronDownIcon from "@/assets/icons/chevron-down.svg";
import historyIcon from "@/assets/icons/history.svg";
import homeIcon from "@/assets/icons/home.svg";
import infoIcon from "@/assets/icons/info.svg";
import libraryIcon from "@/assets/icons/library.svg";
import messageIcon from "@/assets/icons/message.svg";

const mainSections = [
  { to: "/", label: "首页", icon: homeIcon },
  { to: "/problems", label: "题库", icon: libraryIcon },
  { to: "/discuss", label: "讨论", icon: messageIcon },
  { to: "/submissions", label: "提交记录", icon: historyIcon },
] as const;

const aboutSection = { to: "/about", label: "关于", icon: infoIcon } as const;

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
        <Icon src={section.icon} />
        <span className="flex-1">{section.label}</span>
        {isCurrent && <Icon src={checkIcon} size={14} />}
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
        <Icon src={current.icon} />
        {current.label}
        <Icon src={chevronDownIcon} size={14} />
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
