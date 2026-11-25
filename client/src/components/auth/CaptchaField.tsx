import type { useCaptcha } from "@/api/auth";
import FormField from "@/components/ui/FormField";
import { useCopy } from "@/i18n";

type CaptchaFieldProps = {
  captcha: ReturnType<typeof useCaptcha>;
  /** Called with the typed code; usually `captcha.setCode` plus clearing the field's error. */
  onChange: (value: string) => void;
  error?: string;
};

/**
 * The image captcha row: the answer input, and the image beside it, which reloads on click.
 * Both are disabled while the captcha is locked.
 */
export default function CaptchaField({
  captcha,
  onChange,
  error,
}: CaptchaFieldProps) {
  const copy = useCopy();
  return (
    <div className="flex items-start gap-3">
      <FormField
        className="min-w-0 flex-1"
        label={copy.auth.captcha}
        placeholder={copy.auth.captchaPlaceholder}
        autoComplete="off"
        spellCheck={false}
        maxLength={16}
        disabled={captcha.locked}
        value={captcha.code}
        onChange={(event) => onChange(event.target.value)}
        error={error}
      />
      {/* Clears the label row so the image lines up with the input. */}
      <div className="pt-6.5">
        <button
          type="button"
          disabled={captcha.locked}
          onClick={captcha.refresh}
          title={copy.auth.captchaRefresh}
          aria-label={copy.auth.captchaRefresh}
          className="flex h-10 w-29 items-center justify-center overflow-hidden
            rounded-control border border-line bg-layer-inner text-caption
            text-fg-subtle disabled:cursor-not-allowed disabled:opacity-50"
        >
          {captcha.captcha ? (
            <img
              src={captcha.captcha.image}
              alt=""
              className="size-full object-contain"
            />
          ) : (
            captcha.failed && copy.auth.captchaFailed
          )}
        </button>
      </div>
    </div>
  );
}
