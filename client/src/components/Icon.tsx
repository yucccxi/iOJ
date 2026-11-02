type IconProps = {
  src: string;
  size?: 14 | 16;
  className?: string;
};

/** Renders an SVG file as a mask so the icon takes the current text color. */
export default function Icon({ src, size = 16, className = "" }: IconProps) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block shrink-0 bg-current ${className}`}
      style={{
        width: size,
        height: size,
        // Vite inlines small SVGs as data URIs, which break url() unless quoted.
        maskImage: `url("${src}")`,
        maskSize: "100% 100%",
        maskRepeat: "no-repeat",
      }}
    />
  );
}
