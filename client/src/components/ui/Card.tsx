import type { ReactNode } from "react";

type CardProps = {
  title: string;
  /** Text on the right of the title. */
  meta?: ReactNode;
  /** Icon button pinned to the right of the title; positioned so it doesn't make the header taller. */
  action?: ReactNode;
  className?: string;
  children: ReactNode;
};

export default function Card({
  title,
  meta,
  action,
  className = "",
  children,
}: CardProps) {
  return (
    <section
      className={`flex flex-col gap-card-gap rounded-layer bg-layer px-card-x pt-card-y-text pb-card-y-edge shadow-layer ring-1 ring-line-glass ring-inset glass:backdrop-blur-layer ${className}`}
    >
      <header className="relative flex items-center justify-between gap-4">
        <h2 className="text-card text-fg">{title}</h2>
        {meta}
        {action && (
          <div className="absolute top-1/2 -right-1.5 -translate-y-1/2">
            {action}
          </div>
        )}
      </header>
      {children}
    </section>
  );
}
