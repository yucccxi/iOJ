import { createFileRoute } from "@tanstack/react-router";
import { copy } from "@/i18n";

export const Route = createFileRoute("/about")({
  component: () => <h1 className="text-xl font-semibold">{copy.nav.about}</h1>,
});
