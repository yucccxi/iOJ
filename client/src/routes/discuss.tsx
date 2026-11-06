import { createFileRoute } from "@tanstack/react-router";
import { copy } from "@/i18n";

export const Route = createFileRoute("/discuss")({
  component: () => (
    <h1 className="text-xl font-semibold">{copy.nav.discuss}</h1>
  ),
});
