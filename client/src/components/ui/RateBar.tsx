import Tooltip from "@/components/ui/Tooltip";
import { acceptanceRate } from "@/lib/format";

type RateBarProps = { accepted: number; submitted: number };

/** Acceptance bar and percentage; the raw counts show in a tooltip. */
export default function RateBar({ accepted, submitted }: RateBarProps) {
  const rate = acceptanceRate(accepted, submitted);
  return (
    <div className="relative z-10 flex items-center gap-2 pr-2">
      <Tooltip
        label={
          <>
            {/* A full space is a whole mono cell; 2px either side keeps the pair together. */}
            {accepted}
            <span className="mx-0.5">/</span>
            {submitted}
          </>
        }
        labelClassName="font-mono text-mono-label"
      >
        <span className="block h-1.5 w-16 overflow-hidden rounded-full bg-line">
          <span
            className="block h-full rounded-full bg-accent"
            style={{ width: `${(rate ?? 0) * 100}%` }}
          />
        </span>
      </Tooltip>
      <span
        className={`w-11 font-mono text-mono-label text-fg-muted ${rate === null ? "text-center" : "text-right"}`}
      >
        {rate === null ? "—" : `${(rate * 100).toFixed(1)}%`}
      </span>
    </div>
  );
}
