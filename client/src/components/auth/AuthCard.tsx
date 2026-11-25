import type { ReactNode } from "react";

type AuthCardProps = {
  title: string;
  subtitle: string;
  /** The line under the card that links to the other auth page. */
  footer: ReactNode;
  children: ReactNode;
};

/** The 400px card centered in the page body, shared by sign-in and sign-up. */
export default function AuthCard({
  title,
  subtitle,
  footer,
  children,
}: AuthCardProps) {
  return (
    <div className="m-auto flex w-full max-w-100 flex-col items-center gap-5">
      <section
        className="flex w-full flex-col gap-form-section-gap rounded-layer
          bg-layer px-form pt-form-y-text pb-form shadow-layer ring-1
          ring-line-glass ring-inset glass:backdrop-blur-layer"
      >
        <header className="flex flex-col gap-1.5">
          <h1 className="text-title text-fg">{title}</h1>
          <p className="text-field text-fg-muted">{subtitle}</p>
        </header>
        {children}
      </section>
      {footer}
    </div>
  );
}
