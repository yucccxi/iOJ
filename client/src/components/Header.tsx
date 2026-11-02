import { Link } from "@tanstack/react-router";
import { ChevronDown, SquarePlus, User } from "lucide-react";
import SectionSwitcher from "@/components/SectionSwitcher";
import logoUrl from "@/assets/logo.svg";

export default function Header() {
  return (
    <header className="flex h-11 items-center justify-between">
      <nav aria-label="面包屑" className="flex items-center gap-2.5">
        <Link
          to="/"
          className="flex items-center gap-2 text-card tracking-tight text-fg"
        >
          <img src={logoUrl} alt="" className="size-7" />
          iOJ
        </Link>
        <span aria-hidden="true" className="text-body text-fg-subtle">
          /
        </span>
        <SectionSwitcher />
      </nav>

      <div className="flex items-center gap-3">
        <Link
          to="/problems/new"
          className="flex items-center gap-1.5 rounded-control bg-secondary px-3 py-1.5 text-label text-fg"
        >
          <SquarePlus size={16} absoluteStrokeWidth />
          贡献题目
        </Link>
        <Link
          to="/login"
          aria-label="登录"
          className="flex items-center gap-2 rounded-full p-1"
        >
          <span className="flex size-8 items-center justify-center rounded-full border border-line bg-popover text-fg">
            <User size={16} absoluteStrokeWidth />
          </span>
          <ChevronDown size={14} absoluteStrokeWidth className="text-fg-subtle" />
        </Link>
      </div>
    </header>
  );
}
