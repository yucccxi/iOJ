import { createFileRoute } from "@tanstack/react-router";
import { useCopy } from "@/i18n";

function ProfilePage() {
  const copy = useCopy();
  return <h1 className="text-xl font-semibold">{copy.page.profile}</h1>;
}

export const Route = createFileRoute("/profile")({ component: ProfilePage });
