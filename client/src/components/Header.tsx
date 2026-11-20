import { Link } from "@tanstack/react-router";
import { SquarePlus } from "lucide-react";
import AccountMenu from "@/components/AccountMenu";
import SectionSwitcher from "@/components/SectionSwitcher";
import { useCopy } from "@/i18n";
import logoUrl from "@/assets/logo.svg";

export default function Header() {
  const copy = useCopy();
  return (
    <header className="flex h-11 items-center justify-between">
      <nav aria-label="breadcrumb" className="flex items-center gap-2.5">
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
          className="flex items-center gap-1.5 rounded-control bg-secondary px-3
            py-1.5 text-label text-fg"
        >
          <SquarePlus />
          {copy.nav.contribute}
        </Link>
        <AccountMenu />
      </div>
    </header>
  );
}
