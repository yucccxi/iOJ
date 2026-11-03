import { useEffect, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { createFileRoute, stripSearchParams } from "@tanstack/react-router";
import { ArrowUpDown } from "lucide-react";
import { z } from "zod";
import { catalogQuery } from "@/api/catalog";
import ContributorsCard from "@/components/problems/ContributorsCard";
import DifficultyCard from "@/components/problems/DifficultyCard";
import PopularTagsCard from "@/components/problems/PopularTagsCard";
import ProblemTable from "@/components/problems/ProblemTable";
import Card from "@/components/ui/Card";
import Pagination from "@/components/ui/Pagination";
import SearchInput from "@/components/ui/SearchInput";
import SelectButton from "@/components/ui/SelectButton";
import { formatCount } from "@/lib/format";

const PAGE_SIZE = 20;
const SEARCH_DELAY_MS = 300;

const searchDefaults = { page: 1, keyword: "" };
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
});

export const Route = createFileRoute("/problems/")({
  validateSearch: searchSchema,
  // Keep /problems clean instead of /problems?page=1&keyword=.
  search: { middlewares: [stripSearchParams(searchDefaults)] },
  component: ProblemsPage,
});

// The catalog takes one value per filter and has no sort; the buttons stay inert
// until it supports multi-select.
const filters = ["来源", "难度", "标签"];

function ProblemsPage() {
  const { page, keyword } = Route.useSearch();
  const navigate = Route.useNavigate();
  const [draft, setDraft] = useState(keyword);

  useEffect(() => {
    const next = draft.trim();
    if (next === keyword) return;
    const timer = setTimeout(
      () => navigate({ search: { page: 1, keyword: next }, replace: true }),
      SEARCH_DELAY_MS,
    );
    return () => clearTimeout(timer);
  }, [draft, keyword, navigate]);

  const { data, error, isFetching, isPlaceholderData, refetch } = useQuery({
    ...catalogQuery({ keyword, page, size: PAGE_SIZE }),
    // Keep the previous page on screen while the next one loads.
    placeholderData: keepPreviousData,
  });

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
    keyword ? (
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
        <div className="flex items-center gap-2">
          <SearchInput
            value={draft}
            onChange={setDraft}
            placeholder="搜索题目名称..."
            className="min-w-0 flex-1"
          />
          {filters.map((label) => (
            <SelectButton
              key={label}
              label={label}
              disabled
              title="筛选功能即将开放"
            />
          ))}
          <button
            type="button"
            disabled
            aria-label="排序"
            title="排序功能即将开放"
            className="flex size-9 shrink-0 items-center justify-center rounded-control border border-line bg-surface text-fg-muted disabled:cursor-not-allowed"
          >
            <ArrowUpDown />
          </button>
        </div>

        <div
          className={`transition-opacity overflow-hidden ${isFetching && isPlaceholderData ? "opacity-60" : ""}`}
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
