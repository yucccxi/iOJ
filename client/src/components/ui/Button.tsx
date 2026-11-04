import type { ComponentProps } from "react";

const variants = {
  primary: "border-transparent bg-accent text-accent-on",
  secondary: "border-secondary-line bg-secondary text-fg",
};

type ButtonProps = ComponentProps<"button"> & {
  variant?: keyof typeof variants;
};

export default function Button({
  variant = "primary",
  className = "",
  ...props
}: ButtonProps) {
  return (
    <button
      type="button"
      // Both variants carry a border so they stay the same height side by side.
      className={`flex shrink-0 items-center justify-center gap-1.5
        rounded-control border px-3 py-1.5 text-label
        enabled:hover:brightness-95 disabled:cursor-not-allowed
        disabled:opacity-50 ${variants[variant]} ${className}`}
      {...props}
    />
  );
}
