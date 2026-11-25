import { Link } from "@tanstack/react-router";
import { Library } from "lucide-react";
import { useCopy } from "@/i18n";
import logoUrl from "@/assets/logo.svg";

/** The header on sign-in and sign-up: the logo and a way back to the problems, nothing else. */
export default function AuthHeader() {
  const copy = useCopy();
  return (
    <header className="flex h-11 items-center justify-between">
      <Link
        to="/"
        className="flex items-center gap-2 text-card tracking-tight text-fg"
      >
        <img src={logoUrl} alt="" className="size-7" />
        iOJ
      </Link>
      <Link
        to="/problems"
        className="flex items-center gap-1.5 rounded-control bg-secondary px-3
          py-1.5 text-label text-fg"
      >
        <Library />
        {copy.nav.browseProblems}
      </Link>
    </header>
  );
}
