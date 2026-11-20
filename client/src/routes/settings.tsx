import { createFileRoute } from "@tanstack/react-router";
import { useCopy } from "@/i18n";

function SettingsPage() {
  const copy = useCopy();
  return <h1 className="text-xl font-semibold">{copy.page.settings}</h1>;
}

export const Route = createFileRoute("/settings")({ component: SettingsPage });
