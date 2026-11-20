import { createFileRoute } from "@tanstack/react-router";
import { useCopy } from "@/i18n";

function ProblemDetailPage() {
  const copy = useCopy();
  return <h1 className="text-xl font-semibold">{copy.page.problemDetail}</h1>;
}

export const Route = createFileRoute("/problems/$problemId")({
  component: ProblemDetailPage,
});
