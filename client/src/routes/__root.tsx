import { Outlet, createRootRoute } from "@tanstack/react-router";
import { TanStackRouterDevtools } from "@tanstack/react-router-devtools";
import Header from "@/components/Header";

export const Route = createRootRoute({
  component: RootLayout,
});

function RootLayout() {
  return (
    <div className="min-h-svh bg-canvas px-4 pt-5 text-fg">
      <div className="mx-auto max-w-[1300px]">
        <Header />
        <main className="pt-6">
          <Outlet />
        </main>
      </div>
      <TanStackRouterDevtools />
    </div>
  );
}
