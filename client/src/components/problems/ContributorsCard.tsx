import { useQuery } from "@tanstack/react-query";
import { catalogQuery } from "@/api/catalog";
import type { CatalogItem } from "@/api/types";
import Card from "@/components/ui/Card";
import UserChip from "@/components/ui/UserChip";
import { formatCount } from "@/lib/format";

// The largest page the API returns. Counting authors needs every local problem in one page.
const MAX_PAGE = 100;
const SHOWN = 5;

function topAuthors(items: CatalogItem[]) {
  const counts = new Map<number, { name: string; count: number }>();
  for (const item of items) {
    if (item.authorId === null) continue;
    const entry = counts.get(item.authorId) ?? {
      name: item.authorNickname ?? "—",
      count: 0,
    };
    entry.count += 1;
    counts.set(item.authorId, entry);
  }
  return [...counts.entries()]
    .sort((a, b) => b[1].count - a[1].count)
    .slice(0, SHOWN);
}

/** Problems uploaded here and who uploaded the most. */
export default function ContributorsCard() {
  const { data } = useQuery(catalogQuery({ kind: "LOCAL", size: MAX_PAGE }));
  // Without a stats endpoint the ranking is exact only while everything fits in one page.
  const authors =
    data && data.total <= data.items.length ? topAuthors(data.items) : [];

  return (
    <Card
      title="本站贡献"
      meta={
        <span className="text-caption text-fg-subtle">
          {data ? `${formatCount(data.total)} 道用户题目` : "—"}
        </span>
      }
    >
      {authors.length > 0 && (
        <ul className="flex flex-col gap-card-row-gap">
          {authors.map(([id, { name, count }]) => (
            <li key={id} className="flex items-center justify-between gap-3">
              <UserChip name={name} />
              <span className="shrink-0 text-meta text-fg-muted">
                {count} 题
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
