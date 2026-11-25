import { useState } from "react";
import type { FormEvent } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useMutation } from "@tanstack/react-query";
import { z } from "zod";
import { useCaptcha, useSignIn } from "@/api/auth";
import { ApiError } from "@/api/client";
import AuthCard from "@/components/auth/AuthCard";
import AuthSwitch from "@/components/auth/AuthSwitch";
import CaptchaField from "@/components/auth/CaptchaField";
import Button from "@/components/ui/Button";
import Checkbox from "@/components/ui/Checkbox";
import FormField from "@/components/ui/FormField";
import PasswordField from "@/components/ui/PasswordField";
import { useCopy } from "@/i18n";
import type { Copy } from "@/i18n";
import githubUrl from "@/assets/github.svg";

// `account` is set after sign-up, to prefill the new username.
const searchSchema = z.object({
  account: z.string().optional().catch(undefined),
});

export const Route = createFileRoute("/login")({
  validateSearch: searchSchema,
  staticData: { minimalHeader: true },
  component: LoginPage,
});

type Field = "account" | "password" | "captcha" | "form";
type Errors = Partial<Record<Field, string>>;

function errorFor(error: Error, messages: Copy["auth"]["errors"]): Errors {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 2005:
        return { password: messages.credentialsInvalid };
      case 2004:
        return { account: messages.accountBanned };
      case 2010:
        return { captcha: messages.captchaInvalid };
    }
  }
  return { form: error.message };
}

function LoginPage() {
  const copy = useCopy();
  const text = copy.auth.login;
  const { account: registeredAccount } = Route.useSearch();
  const navigate = useNavigate();
  const signIn = useSignIn();
  const captcha = useCaptcha("LOGIN");

  const [account, setAccount] = useState(registeredAccount ?? "");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [errors, setErrors] = useState<Errors>({});

  const mutation = useMutation({
    mutationFn: () =>
      signIn(
        { account: account.trim(), password, ...captcha.answer() },
        remember,
      ),
    onSuccess: () => navigate({ to: "/" }),
    onError: (error) => {
      // The backend consumes the captcha before it checks the password, so any failure needs a new one.
      captcha.refresh();
      setErrors(errorFor(error, copy.auth.errors));
    },
  });

  function edit(field: Exclude<Field, "form">, set: (value: string) => void) {
    return (value: string) => {
      set(value);
      setErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
    };
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (mutation.isPending) return;
    const next: Errors = {};
    if (!account.trim()) next.account = copy.auth.errors.accountRequired;
    if (!password) next.password = copy.auth.errors.passwordRequired;
    if (captcha.enabled && !captcha.code.trim())
      next.captcha = copy.auth.errors.captchaRequired;
    setErrors(next);
    if (Object.keys(next).length === 0) mutation.mutate();
  }

  return (
    <AuthCard
      title={text.title}
      subtitle={registeredAccount ? text.registeredSubtitle : text.subtitle}
      footer={
        <AuthSwitch
          prompt={text.switchPrompt}
          link={text.switchLink}
          to="/register"
        />
      }
    >
      <form
        noValidate
        onSubmit={handleSubmit}
        className="flex flex-col gap-form-field-gap"
      >
        <div className="flex flex-col gap-form-field-gap">
          <FormField
            label={text.account}
            autoComplete="username"
            autoFocus={!registeredAccount}
            maxLength={128}
            value={account}
            onChange={(event) =>
              edit("account", setAccount)(event.target.value)
            }
            error={errors.account}
          />
          <PasswordField
            label={text.password}
            // Password reset has no page yet; the link is a placeholder.
            labelAside={
              <button type="button" className="text-accent-strong">
                {text.forgotPassword}
              </button>
            }
            autoComplete="current-password"
            autoFocus={Boolean(registeredAccount)}
            maxLength={64}
            value={password}
            onChange={(event) =>
              edit("password", setPassword)(event.target.value)
            }
            error={errors.password}
          />
          {captcha.enabled && (
            <CaptchaField
              captcha={captcha}
              onChange={edit("captcha", captcha.setCode)}
              error={errors.captcha}
            />
          )}
        </div>

        <div className="flex flex-col gap-form-stack-gap">
          <label
            className="flex items-center gap-2 self-start text-label
              text-fg-muted"
          >
            <Checkbox
              checked={remember}
              onChange={(event) => setRemember(event.target.checked)}
            />
            {text.remember}
          </label>
          {errors.form && (
            <p role="alert" className="text-caption text-status-wa">
              {errors.form}
            </p>
          )}
          <Button
            type="submit"
            size="lg"
            className="w-full"
            disabled={mutation.isPending}
          >
            {text.submit}
          </Button>
        </div>
      </form>

      <div className="flex flex-col gap-form-stack-gap">
        <div className="flex items-center gap-3 text-caption text-fg-subtle">
          <div className="h-px flex-1 bg-line" />
          {text.or}
          <div className="h-px flex-1 bg-line" />
        </div>
        {/* GitHub sign-in is not implemented; the button is a placeholder. */}
        <Button variant="secondary" size="lg" className="w-full">
          <span
            aria-hidden="true"
            // A mask so the stroke follows the text color in every theme.
            className="size-4.5 bg-current mask-contain mask-center
              mask-no-repeat"
            // Quoted: Vite inlines the SVG as a data URL with single quotes, which an unquoted
            // url() rejects, leaving a solid square.
            style={{ maskImage: `url("${githubUrl}")` }}
          />
          {text.github}
        </Button>
      </div>
    </AuthCard>
  );
}
