import { Link } from "@tanstack/react-router";

type AuthSwitchProps = {
  prompt: string;
  link: string;
  to: "/login" | "/register";
};

export default function AuthSwitch({ prompt, link, to }: AuthSwitchProps) {
  return (
    <p className="flex gap-1 text-field text-fg-muted">
      {prompt}
      <Link to={to} className="text-accent-strong hover:underline">
        {link}
      </Link>
    </p>
  );
}
