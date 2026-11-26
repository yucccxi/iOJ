import { useRef, useState } from "react";
import type { FormEvent, RefObject } from "react";
import { flushSync } from "react-dom";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useMutation } from "@tanstack/react-query";
import { ChevronLeft } from "lucide-react";
import { sendRegisterCode, useCaptcha, useSignUp } from "@/api/auth";
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
import { isEmail, isEmailCode, isUsername, passwordStrength } from "@/lib/auth";
import type { PasswordStrength } from "@/lib/auth";
import { useCountdown } from "@/lib/useCountdown";

export const Route = createFileRoute("/register")({
  staticData: { minimalHeader: true },
  component: RegisterPage,
});

type EmailErrors = Partial<Record<"email" | "captcha" | "code", string>>;
type AccountErrors = Partial<
  Record<"username" | "password" | "confirm" | "terms" | "form", string>
>;
type ErrorCopy = Copy["auth"]["errors"];

const strengthColors: Record<PasswordStrength, string> = {
  weak: "text-difficulty-hard",
  medium: "text-difficulty-medium",
  strong: "text-difficulty-easy",
};

// The backend reports a wrong and an expired code with the same 2007; the local clock tells them apart.
function codeRejection(expiresAt: number | null, messages: ErrorCopy) {
  return expiresAt !== null && Date.now() >= expiresAt
    ? messages.codeExpired
    : messages.codeIncorrect;
}

function RegisterPage() {
  const copy = useCopy();
  const text = copy.auth.register;
  const [step, setStep] = useState<"email" | "account">("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [codeExpiresAt, setCodeExpiresAt] = useState<number | null>(null);
  const [emailErrors, setEmailErrors] = useState<EmailErrors>({});

  const emailRef = useRef<HTMLInputElement>(null);
  const codeRef = useRef<HTMLInputElement>(null);
  const usernameRef = useRef<HTMLInputElement>(null);

  // Both steps stay mounted so going back keeps what was typed; flushSync shows the
  // step before focusing into it.
  function showAccountStep() {
    flushSync(() => setStep("account"));
    usernameRef.current?.focus();
  }

  function showEmailStep() {
    flushSync(() => setStep("email"));
    emailRef.current?.focus();
  }

  function rejectEmailStep(errors: EmailErrors) {
    flushSync(() => {
      setEmailErrors(errors);
      setStep("email");
    });
    (errors.email ? emailRef : codeRef).current?.focus();
  }

  return (
    <AuthCard
      title={text.title}
      subtitle={step === "email" ? text.stepEmail : text.stepAccount}
      footer={
        <AuthSwitch
          prompt={text.switchPrompt}
          link={text.switchLink}
          to="/login"
        />
      }
    >
      <EmailStep
        hidden={step !== "email"}
        email={email}
        onEmailChange={setEmail}
        code={code}
        onCodeChange={setCode}
        codeExpiresAt={codeExpiresAt}
        onCodeSent={setCodeExpiresAt}
        errors={emailErrors}
        onErrorsChange={setEmailErrors}
        emailRef={emailRef}
        codeRef={codeRef}
        onNext={showAccountStep}
      />
      <AccountStep
        hidden={step !== "account"}
        email={email}
        code={code}
        usernameRef={usernameRef}
        onBack={showEmailStep}
        onEmailRejected={(error) =>
          rejectEmailStep(
            error.code === 2003
              ? { email: copy.auth.errors.emailTaken }
              : { code: codeRejection(codeExpiresAt, copy.auth.errors) },
          )
        }
      />
    </AuthCard>
  );
}

type EmailStepProps = {
  hidden: boolean;
  email: string;
  onEmailChange: (email: string) => void;
  code: string;
  onCodeChange: (code: string) => void;
  codeExpiresAt: number | null;
  onCodeSent: (expiresAt: number) => void;
  errors: EmailErrors;
  onErrorsChange: (update: (prev: EmailErrors) => EmailErrors) => void;
  emailRef: RefObject<HTMLInputElement | null>;
  codeRef: RefObject<HTMLInputElement | null>;
  onNext: () => void;
};

function EmailStep({
  hidden,
  email,
  onEmailChange,
  code,
  onCodeChange,
  codeExpiresAt,
  onCodeSent,
  errors,
  onErrorsChange,
  emailRef,
  codeRef,
  onNext,
}: EmailStepProps) {
  const copy = useCopy();
  const text = copy.auth.register;
  const messages = copy.auth.errors;
  const captcha = useCaptcha("EMAIL_SENDING");
  const [validMinutes, setValidMinutes] = useState<number | null>(null);
  const [resendLeft, startResend] = useCountdown();

  const send = useMutation({
    mutationFn: () =>
      sendRegisterCode({ email: email.trim(), ...captcha.answer() }),
    onSuccess: (sent) => {
      // The server accepted and used the captcha: keep it visible but read-only, and fetch a
      // new one only when another send is allowed.
      if (captcha.enabled) {
        captcha.lock();
        startResend(sent.resendAfterSeconds, captcha.refresh);
      } else {
        startResend(sent.resendAfterSeconds);
      }
      setValidMinutes(Math.round(sent.expiresInSeconds / 60));
      onCodeSent(Date.now() + sent.expiresInSeconds * 1000);
      codeRef.current?.focus();
    },
    onError: (error) => {
      // The captcha is checked first and used up even when a later check fails.
      captcha.refresh();
      onErrorsChange(() => sendErrorFor(error, messages));
    },
  });

  function edit(field: keyof EmailErrors, set: (value: string) => void) {
    return (value: string) => {
      set(value);
      onErrorsChange((prev) => ({ ...prev, [field]: undefined }));
    };
  }

  function emailError(): string | undefined {
    if (!email.trim()) return messages.emailRequired;
    if (!isEmail(email.trim())) return messages.emailInvalid;
    return undefined;
  }

  function handleSend() {
    const next: EmailErrors = { email: emailError() };
    if (captcha.enabled && !captcha.code.trim())
      next.captcha = messages.captchaRequired;
    onErrorsChange(() => next);
    if (!next.email && !next.captcha) send.mutate();
  }

  function handleNext(event: FormEvent) {
    event.preventDefault();
    const next: EmailErrors = { email: emailError() };
    if (!code) next.code = messages.codeRequired;
    else if (!isEmailCode(code)) next.code = messages.codeFormat;
    else if (codeExpiresAt !== null && Date.now() >= codeExpiresAt)
      next.code = messages.codeExpired;
    onErrorsChange(() => next);
    if (!next.email && !next.code) onNext();
  }

  const sendLabel =
    resendLeft > 0
      ? text.resendIn(resendLeft)
      : codeExpiresAt === null
        ? text.sendCode
        : text.resend;

  return (
    <form
      noValidate
      hidden={hidden}
      onSubmit={handleNext}
      className="flex flex-col gap-form-field-gap"
    >
      <div className="mb-1 flex flex-col gap-form-field-gap">
        <FormField
          ref={emailRef}
          label={text.email}
          type="email"
          autoComplete="email"
          autoFocus
          maxLength={128}
          value={email}
          onChange={(event) => edit("email", onEmailChange)(event.target.value)}
          error={errors.email}
        />
        {captcha.enabled && (
          <CaptchaField
            captcha={captcha}
            onChange={edit("captcha", captcha.setCode)}
            error={errors.captcha}
          />
        )}
        <div className="flex items-start gap-3">
          <FormField
            ref={codeRef}
            className="min-w-0 flex-1"
            label={text.code}
            placeholder={text.codePlaceholder}
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(event) => edit("code", onCodeChange)(event.target.value)}
            helper={
              validMinutes === null ? undefined : text.codeSent(validMinutes)
            }
            error={errors.code}
          />
          {/* Clears the label row so the button lines up with the input. */}
          <div className="pt-6.5">
            <Button
              variant="secondary"
              size="lg"
              disabled={resendLeft > 0 || send.isPending}
              onClick={handleSend}
            >
              {sendLabel}
            </Button>
          </div>
        </div>
      </div>
      <Button type="submit" size="lg" className="mt-1 w-full">
        {text.next}
      </Button>
    </form>
  );
}

function sendErrorFor(error: Error, messages: ErrorCopy): EmailErrors {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 2003:
        return { email: messages.emailTaken };
      case 1001:
        return { email: error.message };
      case 2010:
        return { captcha: messages.captchaInvalid };
      case 2008:
        return { code: messages.codeTooFrequent };
      case 2009:
        return { code: messages.mailFailed };
    }
  }
  // The send button sits on the code row, so other failures show there.
  return { code: error.message };
}

type AccountStepProps = {
  hidden: boolean;
  email: string;
  code: string;
  usernameRef: RefObject<HTMLInputElement | null>;
  onBack: () => void;
  /** Called with a 2003 (email taken) or 2007 (bad code), which step 1 has to fix. */
  onEmailRejected: (error: ApiError) => void;
};

function AccountStep({
  hidden,
  email,
  code,
  usernameRef,
  onBack,
  onEmailRejected,
}: AccountStepProps) {
  const copy = useCopy();
  const text = copy.auth.register;
  const messages = copy.auth.errors;
  const navigate = useNavigate();
  const signUp = useSignUp();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [errors, setErrors] = useState<AccountErrors>({});
  const strength = passwordStrength(password);

  const submit = useMutation({
    mutationFn: () =>
      signUp({ username, email: email.trim(), password, emailCode: code }),
    onSuccess: () => navigate({ to: "/" }),
    onError: (error) => {
      if (error instanceof ApiError && [2003, 2007].includes(error.code)) {
        onEmailRejected(error);
      } else if (error instanceof ApiError && error.code === 2002) {
        setErrors({ username: messages.usernameTaken });
      } else {
        setErrors({ form: error.message });
      }
    },
  });

  function edit(field: keyof AccountErrors, set: (value: string) => void) {
    return (value: string) => {
      set(value);
      setErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
    };
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submit.isPending) return;
    const next: AccountErrors = {};
    if (!username) next.username = messages.usernameRequired;
    else if (!isUsername(username)) next.username = messages.usernameInvalid;
    if (!password) next.password = messages.passwordRequired;
    else if (strength === "weak") next.password = messages.passwordWeak;
    if (!confirm) next.confirm = messages.confirmRequired;
    else if (confirm !== password) next.confirm = messages.passwordMismatch;
    if (!agreed) next.terms = messages.termsRequired;
    setErrors(next);
    if (Object.keys(next).length === 0) submit.mutate();
  }

  return (
    <form
      noValidate
      hidden={hidden}
      onSubmit={handleSubmit}
      className="flex flex-col gap-form-field-gap"
    >
      <button
        type="button"
        onClick={onBack}
        className="-ml-0.5 flex items-center gap-0.5 self-start text-label
          text-fg-muted hover:text-fg"
      >
        <ChevronLeft />
        {text.back}
      </button>
      <div className="flex flex-col gap-form-field-gap">
        <FormField
          ref={usernameRef}
          label={text.username}
          autoComplete="username"
          spellCheck={false}
          maxLength={32}
          value={username}
          onChange={(event) =>
            edit("username", setUsername)(event.target.value)
          }
          error={errors.username}
        />
        <PasswordField
          label={text.password}
          labelAside={
            password && (
              <span className="text-fg-subtle">
                {text.strength}
                <span className={strengthColors[strength]}>
                  {text.strengths[strength]}
                </span>
              </span>
            )
          }
          autoComplete="new-password"
          maxLength={64}
          value={password}
          onChange={(event) =>
            edit("password", setPassword)(event.target.value)
          }
          helper={text.passwordHelper}
          error={errors.password}
        />
        <PasswordField
          label={text.confirmPassword}
          placeholder={text.confirmPlaceholder}
          autoComplete="new-password"
          maxLength={64}
          value={confirm}
          onChange={(event) => edit("confirm", setConfirm)(event.target.value)}
          error={errors.confirm}
        />
      </div>

      <div className="flex flex-col gap-form-stack-gap">
        <div className="flex flex-col gap-1">
          {/* The agreement pages don't exist yet; the titles are styled as links but inert. */}
          <label className="flex items-center gap-2 self-start text-label">
            <Checkbox
              checked={agreed}
              onChange={(event) => {
                setAgreed(event.target.checked);
                setErrors((prev) => ({ ...prev, terms: undefined }));
              }}
            />
            <span className="text-fg-muted">
              {text.agreeLead}
              <span className="text-accent-strong">{text.terms}</span>
              {text.agreeJoiner}
              <span className="text-accent-strong">{text.privacy}</span>
            </span>
          </label>
          {errors.terms && (
            <p className="text-caption text-status-wa">{errors.terms}</p>
          )}
        </div>
        {errors.form && (
          <p role="alert" className="text-caption text-status-wa">
            {errors.form}
          </p>
        )}
        <Button
          type="submit"
          size="lg"
          className="mt-1 w-full"
          disabled={submit.isPending}
        >
          {text.submit}
        </Button>
      </div>
    </form>
  );
}
