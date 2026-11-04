import { useEffect, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { createFileRoute, stripSearchParams } from "@tanstack/react-router";
import { z } from "zod";
import {
  catalogQuery,
  catalogSorts,
  type CatalogSort,
  type SortOrder,
  type TagMatch,
} from "@/api/catalog";
import { difficulties, type Difficulty } from "@/api/types";
import ContributorsCard from "@/components/problems/ContributorsCard";
import DifficultyCard from "@/components/problems/DifficultyCard";
import DifficultyFilter from "@/components/problems/DifficultyFilter";
import PopularTagsCard from "@/components/problems/PopularTagsCard";
import ProblemTable from "@/components/problems/ProblemTable";
import SortMenu from "@/components/problems/SortMenu";
import SourceFilter from "@/components/problems/SourceFilter";
import TagFilter from "@/components/problems/TagFilter";
import Card from "@/components/ui/Card";
import Pagination from "@/components/ui/Pagination";
import SearchInput from "@/components/ui/SearchInput";
import { formatCount } from "@/lib/format";

const PAGE_SIZE = 20;
const SEARCH_DELAY_MS = 300;

const searchDefaults = {
  page: 1,
  keyword: "",
  scopeId: [] as number[],
  difficulty: [] as Difficulty[],
  tagId: [] as number[],
  tagMatch: "ANY" as TagMatch,
  sort: "DEFAULT" as CatalogSort,
  order: "DESC" as SortOrder,
};
const searchSchema = z.object({
  page: z
    .number()
    .int()
    .min(1)
    .default(searchDefaults.page)
    .catch(searchDefaults.page),
  keyword: z
    .string()
    .default(searchDefaults.keyword)
    .catch(searchDefaults.keyword),
  scopeId: z
    .array(z.number().int())
    .default(searchDefaults.scopeId)
    .catch(searchDefaults.scopeId),
  difficulty: z
    .array(z.enum(difficulties))
    .default(searchDefaults.difficulty)
    .catch(searchDefaults.difficulty),
  tagId: z
    .array(z.number().int())
    .default(searchDefaults.tagId)
    .catch(searchDefaults.tagId),
  tagMatch: z
    .enum(["ANY", "ALL"])
    .default(searchDefaults.tagMatch)
    .catch(searchDefaults.tagMatch),
  sort: z
    .enum(catalogSorts)
    .default(searchDefaults.sort)
    .catch(searchDefaults.sort),
  order: z
    .enum(["ASC", "DESC"])
    .default(searchDefaults.order)
    .catch(searchDefaults.order),
});

type ProblemsSearch = z.infer<typeof searchSchema>;

export const Route = createFileRoute("/problems/")({
  validateSearch: searchSchema,
  // Keep /problems clean instead of /problems?page=1&keyword=.
  search: { middlewares: [stripSearchParams(searchDefaults)] },
  component: ProblemsPage,
});

function ProblemsPage() {
  const { page, keyword, scopeId, difficulty, tagId, tagMatch, sort, order } =
    Route.useSearch();
  const navigate = Route.useNavigate();
  const [draft, setDraft] = useState(keyword);

  // Any change to what is listed starts again from the first page.
  function setFilters(change: Partial<ProblemsSearch>, replace = false) {
    navigate({ search: (prev) => ({ ...prev, ...change, page: 1 }), replace });
  }

  useEffect(() => {
    const next = draft.trim();
    if (next === keyword) return;
    const timer = setTimeout(
      () =>
        navigate({
          search: (prev) => ({ ...prev, keyword: next, page: 1 }),
          replace: true,
        }),
      SEARCH_DELAY_MS,
    );
    return () => clearTimeout(timer);
  }, [draft, keyword, navigate]);

  const { data, error, isFetching, isPlaceholderData, refetch } = useQuery({
    ...catalogQuery({
      keyword,
      scopeId,
      difficulty,
      tagId,
      // Defaults are left out so the request matches what the URL shows.
      tagMatch: tagId.length > 1 && tagMatch !== "ANY" ? tagMatch : undefined,
      sort: sort === "DEFAULT" ? undefined : sort,
      order: order === "DESC" ? undefined : order,
      page,
      size: PAGE_SIZE,
    }),
    // Keep the previous page on screen while the next one loads.
    placeholderData: keepPreviousData,
  });

  const filtered = scopeId.length + difficulty.length + tagId.length > 0;

  const message = error ? (
    <>
      {error.message}
      <button
        type="button"
        onClick={() => refetch()}
        className="ml-2 text-accent-strong hover:underline"
      >
        重试
      </button>
    </>
  ) : data && data.items.length === 0 ? (
    filtered ? (
      "没有符合当前筛选条件的题目"
    ) : keyword ? (
      `没有标题包含「${keyword}」的题目`
    ) : (
      "题库里还没有题目"
    )
  ) : undefined;

  return (
    <div className="flex items-start gap-5 max-lg:flex-col">
      <Card
        title="题目列表"
        meta={
          data && (
            <span className="text-meta text-fg-muted">
              找到 {formatCount(data.total)} 道题目
            </span>
          )
        }
        className="min-w-0 flex-1 max-lg:w-full"
      >
        {/* Positioned so the wide panels can align to the toolbar's right edge. */}
        <div className="relative flex items-center gap-2">
          <SearchInput
            value={draft}
            onChange={setDraft}
            placeholder="搜索题目名称..."
            className="min-w-0 flex-1"
          />
          <SourceFilter
            value={scopeId}
            onChange={(next) =>
              setFilters({
                scopeId: next,
                // The API rejects RATING unless exactly one scope is selected.
                ...(sort === "RATING" &&
                  next.length !== 1 && { sort: "DEFAULT" }),
              })
            }
          />
          <DifficultyFilter
            value={difficulty}
            onChange={(next) => setFilters({ difficulty: next })}
          />
          <TagFilter
            tagId={tagId}
            tagMatch={tagMatch}
            onApply={(nextTagId, nextMatch) =>
              setFilters({ tagId: nextTagId, tagMatch: nextMatch })
            }
          />
          <SortMenu
            sort={sort}
            order={order}
            ratingAllowed={scopeId.length === 1}
            onChange={(change) => setFilters(change)}
          />
        </div>

        <div
          className={`transition-opacity ${isFetching && isPlaceholderData ? "opacity-60" : ""}`}
        >
          <ProblemTable
            items={data?.items}
            skeletonRows={10}
            message={message}
          />
        </div>

        {data && data.total > 0 && (
          <Pagination
            page={page}
            size={PAGE_SIZE}
            total={data.total}
            unit="题"
            onPageChange={(next) =>
              navigate({ search: (prev) => ({ ...prev, page: next }) })
            }
          />
        )}
      </Card>

      <aside className="flex w-88.75 shrink-0 flex-col gap-grid-gap max-lg:w-full">
        <DifficultyCard />
        <PopularTagsCard />
        <ContributorsCard />
      </aside>
    </div>
  );
}
