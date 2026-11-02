import { Link } from "@tanstack/react-router";
import Icon from "@/components/Icon";
import SectionSwitcher from "@/components/SectionSwitcher";
import chevronDownIcon from "@/assets/icons/chevron-down.svg";
import squarePlusIcon from "@/assets/icons/square-plus.svg";
import userIcon from "@/assets/icons/user.svg";

export default function Header() {
  return (
    <header className="flex h-11 items-center justify-between">
      <nav aria-label="面包屑" className="flex items-center gap-2.5">
        <Link
          to="/"
          aria-label="首页"
          className="flex size-7 items-center justify-center rounded-lg bg-accent font-mono text-badge text-accent-on"
        >
          OJ
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
          <Icon src={squarePlusIcon} />
          贡献题目
        </Link>
        <Link
          to="/login"
          aria-label="登录"
          className="flex items-center gap-2 rounded-full p-1"
        >
          <span className="flex size-8 items-center justify-center rounded-full border border-line bg-popover text-fg">
            <Icon src={userIcon} />
          </span>
          <Icon src={chevronDownIcon} size={14} className="text-fg-subtle" />
        </Link>
      </div>
    </header>
  );
}
