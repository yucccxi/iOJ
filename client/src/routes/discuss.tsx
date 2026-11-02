import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/discuss")({
  component: () => <h1 className="text-xl font-semibold">讨论</h1>,
});
