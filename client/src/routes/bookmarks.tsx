import { createFileRoute } from "@tanstack/react-router";
import { useCopy } from "@/i18n";

function BookmarksPage() {
  const copy = useCopy();
  return <h1 className="text-xl font-semibold">{copy.page.bookmarks}</h1>;
}

export const Route = createFileRoute("/bookmarks")({
  component: BookmarksPage,
});
