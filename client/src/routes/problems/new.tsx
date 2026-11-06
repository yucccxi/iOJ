import { createFileRoute } from "@tanstack/react-router";
import { copy } from "@/i18n";

export const Route = createFileRoute("/problems/new")({
  component: () => (
    <h1 className="text-xl font-semibold">{copy.nav.contribute}</h1>
  ),
});
