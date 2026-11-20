import { createFileRoute } from "@tanstack/react-router";
import { useCopy } from "@/i18n";

function AboutPage() {
  const copy = useCopy();
  return <h1 className="text-xl font-semibold">{copy.nav.about}</h1>;
}

export const Route = createFileRoute("/about")({ component: AboutPage });
