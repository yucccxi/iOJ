import { Outlet, createRootRoute } from "@tanstack/react-router";
import { TanStackRouterDevtools } from "@tanstack/react-router-devtools";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import Header from "@/components/Header";

export const Route = createRootRoute({
  component: RootLayout,
});

function RootLayout() {
  return (
    <div className="min-h-svh bg-canvas px-8 pt-5 text-fg">
      <div className="mx-auto max-w-325">
        <Header />
        <main className="pt-6 pb-12">
          <Outlet />
        </main>
      </div>
      <TanStackRouterDevtools />
      <ReactQueryDevtools />
    </div>
  );
}
