import { useState } from "react";
import type { ComponentProps } from "react";
import { Eye, EyeOff } from "lucide-react";
import FormField from "@/components/ui/FormField";
import { useCopy } from "@/i18n";

type PasswordFieldProps = Omit<
  ComponentProps<typeof FormField>,
  "type" | "trailing" | "inputClassName"
>;

/** A FormField with a show/hide toggle. */
export default function PasswordField(props: PasswordFieldProps) {
  const copy = useCopy();
  const [visible, setVisible] = useState(false);
  const Icon = visible ? EyeOff : Eye;

  return (
    <FormField
      {...props}
      type={visible ? "text" : "password"}
      // Noto Sans SC spaces bullets like an ellipsis; the CJK placeholder keeps the sans font.
      inputClassName={visible ? "" : "not-placeholder-shown:font-mono"}
      trailing={
        <button
          type="button"
          aria-label={visible ? copy.auth.hidePassword : copy.auth.showPassword}
          aria-pressed={visible}
          onClick={() => setVisible(!visible)}
          className="flex size-7 shrink-0 items-center justify-center
            rounded-control text-fg-subtle hover:bg-hover hover:text-fg"
        >
          <Icon />
        </button>
      }
    />
  );
}
