const sizes = {
  /** 12px, next to small mono text in table cells. */
  xs: "size-3 font-mono text-mini leading-none",
  /** 28px, for name lists. */
  md: "size-7 text-number",
};

type AvatarProps = { name: string; size: keyof typeof sizes };

/** Initial-letter avatar; there are no profile pictures yet. */
export default function Avatar({ name, size }: AvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={`flex shrink-0 items-center justify-center rounded-full
        bg-accent-subtle text-accent-strong ${sizes[size]}`}
    >
      {name.charAt(0).toUpperCase()}
    </span>
  );
}
