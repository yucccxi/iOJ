import type { ComponentProps } from "react";
import { Check } from "lucide-react";

type CheckboxProps = Omit<ComponentProps<"input">, "type">;

/** A native checkbox restyled; wrap it in a label to name it. */
export default function Checkbox({ className = "", ...props }: CheckboxProps) {
  return (
    <span className={`relative flex size-4 shrink-0 ${className}`}>
      <input
        type="checkbox"
        className="peer size-4 cursor-pointer appearance-none rounded-sm border-[1.5px] border-line bg-surface checked:border-accent checked:bg-accent focus-visible:ring-3 focus-visible:ring-accent-ring focus-visible:outline-none"
        {...props}
      />
      <Check
        size={12}
        className="pointer-events-none absolute inset-0 m-auto hidden text-accent-on peer-checked:block"
      />
    </span>
  );
}
