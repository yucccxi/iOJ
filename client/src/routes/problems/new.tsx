import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/problems/new")({
  component: () => <h1 className="text-xl font-semibold">贡献题目</h1>,
});
