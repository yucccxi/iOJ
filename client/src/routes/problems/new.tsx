import { createFileRoute } from "@tanstack/react-router";
import { useCopy } from "@/i18n";

function NewProblemPage() {
  const copy = useCopy();
  return <h1 className="text-xl font-semibold">{copy.nav.contribute}</h1>;
}

export const Route = createFileRoute("/problems/new")({
  component: NewProblemPage,
});
