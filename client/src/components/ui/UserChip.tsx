import Avatar from "@/components/ui/Avatar";

/** Avatar and name, for lists such as top contributors. */
export default function UserChip({ name }: { name: string }) {
  return (
    <span className="flex min-w-0 items-center gap-2.5">
      <Avatar name={name} size="md" />
      <span className="truncate text-field text-fg">{name}</span>
    </span>
  );
}
