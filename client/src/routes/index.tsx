import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/")({
  component: () => (
    <h1 className="text-brand p-6 text-2xl font-semibold">iOJ</h1>
  ),
});
