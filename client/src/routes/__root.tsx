import { Outlet, createRootRoute, useMatches } from "@tanstack/react-router";
import { TanStackRouterDevtools } from "@tanstack/react-router-devtools";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import AuthHeader from "@/components/auth/AuthHeader";
import Header from "@/components/Header";

export const Route = createRootRoute({
  component: RootLayout,
});

function RootLayout() {
  const minimalHeader = useMatches({
    select: (matches) =>
      matches.some((match) => match.staticData.minimalHeader),
  });

  return (
    // The flex column lets a page fill the height below the header, e.g. to center the auth card.
    <div className="flex min-h-svh flex-col bg-canvas px-8 pt-5 text-fg">
      <div className="mx-auto flex w-full max-w-325 flex-1 flex-col">
        {minimalHeader ? <AuthHeader /> : <Header />}
        <main className="flex flex-1 flex-col pt-6 pb-12">
          <Outlet />
        </main>
      </div>
      <TanStackRouterDevtools />
      <ReactQueryDevtools />
    </div>
  );
}
