import type { ComponentProps } from "react";

const variants = {
  primary: "border-transparent bg-accent text-accent-on",
  secondary: "border-secondary-line bg-secondary text-fg",
};

const sizes = {
  /** 32px, for toolbars and menus. */
  md: "gap-1.5 px-3 py-1.5 text-label",
  /** 40px, matching form inputs. */
  lg: "h-10 gap-2 px-5 text-button",
};

type ButtonProps = ComponentProps<"button"> & {
  variant?: keyof typeof variants;
  size?: keyof typeof sizes;
};

export default function Button({
  variant = "primary",
  size = "md",
  className = "",
  ...props
}: ButtonProps) {
  return (
    <button
      type="button"
      // Both variants carry a border so they stay the same height side by side.
      className={`flex shrink-0 items-center justify-center rounded-control
        border enabled:hover:brightness-95 disabled:cursor-not-allowed
        disabled:opacity-50 ${variants[variant]} ${sizes[size]} ${className}`}
      {...props}
    />
  );
}
