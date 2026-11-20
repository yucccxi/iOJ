import { createFileRoute } from "@tanstack/react-router";
import { useCopy } from "@/i18n";

function ActivityPage() {
  const copy = useCopy();
  return <h1 className="text-xl font-semibold">{copy.page.activity}</h1>;
}

export const Route = createFileRoute("/activity")({ component: ActivityPage });
