import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/problems/$problemId")({
  component: () => <h1 className="text-xl font-semibold">题目详情</h1>,
});
