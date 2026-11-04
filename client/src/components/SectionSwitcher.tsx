import { Link, useLocation } from "@tanstack/react-router";
import {
  Check,
  ChevronDown,
  History,
  House,
  Info,
  Library,
  MessageSquare,
} from "lucide-react";
import Popover from "@/components/ui/Popover";

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
  function renderItem(section: Section, close: () => void) {
    const isCurrent = section === current;
    const SectionIcon = section.icon;
    return (
      <Link
        key={section.to}
        to={section.to}
        role="menuitem"
        activeOptions={{ exact: section.to === "/" }}
        onClick={close}
        className={`flex items-center gap-2.5 rounded-control px-2.5 py-2
          text-label hover:bg-hover ${
            isCurrent ? "text-accent-strong" : "text-fg"
          }`}
      >
        <SectionIcon />
        <span className="flex-1">{section.label}</span>
        {isCurrent && <Check size={14} />}
      </Link>
    );
  }

  return (
    <Popover
      label="切换栏目"
      role="menu"
      className="flex w-50 -translate-x-2 flex-col gap-0.5 p-1.5"
      trigger={(props) => (
        <button
          {...props}
          type="button"
          className="flex items-center gap-1.5 rounded-control bg-selected
            px-2.5 py-1.5 text-field text-accent-strong"
        >
          <current.icon />
          {current.label}
          <ChevronDown size={14} />
        </button>
      )}
    >
      {(close) => (
        <>
          {mainSections.map((section) => renderItem(section, close))}
          <div className="p-1">
            <div className="h-px bg-line" />
          </div>
          {renderItem(aboutSection, close)}
        </>
      )}
    </Popover>
  );
}
