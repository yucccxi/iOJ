export default function Tag({ label }: { label: string }) {
  return (
    <span className="max-w-full shrink-0 truncate rounded-full bg-tag-bg px-2.5 py-1 text-tag text-tag-fg">
      {label}
    </span>
  );
}
