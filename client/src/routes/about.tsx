import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/about")({
  component: () => <h1 className="text-xl font-semibold">关于</h1>,
});
