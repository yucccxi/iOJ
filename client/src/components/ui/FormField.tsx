import { useId } from "react";
import type { ComponentProps, ReactNode } from "react";

type FormFieldProps = Omit<ComponentProps<"input">, "className"> & {
  label: string;
  /** Right side of the label row: a field-specific action or status. */
  labelAside?: ReactNode;
  /** Shown under the input when there is no error. */
  helper?: string;
  error?: string;
  /** Inside the input frame, after the text. */
  trailing?: ReactNode;
  className?: string;
  inputClassName?: string;
};

/** A labelled 40px text input; the message line under it takes space only when present. */
export default function FormField({
  label,
  labelAside,
  helper,
  error,
  trailing,
  className = "",
  inputClassName = "",
  id,
  ...props
}: FormFieldProps) {
  const fallbackId = useId();
  const inputId = id ?? fallbackId;
  const messageId = `${inputId}-message`;
  const message = error ?? helper;

  return (
    <div className={`flex flex-col gap-form-helper-gap ${className}`}>
      <div className="flex flex-col gap-form-label-gap">
        <div className="flex items-center justify-between gap-3 text-label">
          <label htmlFor={inputId} className="text-fg-muted">
            {label}
          </label>
          {labelAside}
        </div>
        <div
          className={`flex h-10 items-center gap-2 rounded-control border
            bg-layer-inner pr-2 pl-3 has-disabled:cursor-not-allowed
            has-disabled:opacity-50 ${
              error
                ? "border-status-wa"
                : `border-line focus-within:ring-2
                  focus-within:ring-accent-ring`
            }`}
        >
          <input
            id={inputId}
            aria-invalid={error ? true : undefined}
            aria-describedby={message ? messageId : undefined}
            className={`h-full min-w-0 flex-1 bg-transparent text-field text-fg
              outline-none placeholder:text-fg-subtle
              disabled:cursor-not-allowed ${inputClassName}`}
            {...props}
          />
          {trailing}
        </div>
      </div>
      {message && (
        <p
          id={messageId}
          className={`text-caption
          ${error ? "text-status-wa" : "text-fg-subtle"}`}
        >
          {message}
        </p>
      )}
    </div>
  );
}
