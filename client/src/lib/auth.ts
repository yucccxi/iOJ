export const passwordStrengths = ["weak", "medium", "strong"] as const;
export type PasswordStrength = (typeof passwordStrengths)[number];

// The backend checks only `@` and length; this catches typos like a missing domain dot.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const USERNAME_PATTERN = /^\w{3,32}$/;
const EMAIL_CODE_PATTERN = /^\d{6}$/;

export function isEmail(value: string): boolean {
  return value.length <= 128 && EMAIL_PATTERN.test(value);
}

export function isUsername(value: string): boolean {
  return USERNAME_PATTERN.test(value);
}

export function isEmailCode(value: string): boolean {
  return EMAIL_CODE_PATTERN.test(value);
}

/**
 * `weak` fails the sign-up rule (8+ characters with a letter and a digit), which is
 * stricter than the backend's 8-64 length check.
 */
export function passwordStrength(password: string): PasswordStrength {
  if (password.length < 8 || !/[a-z]/i.test(password) || !/\d/.test(password))
    return "weak";
  const classes = [/[a-z]/, /[A-Z]/, /\d/, /[^a-zA-Z\d]/].filter((pattern) =>
    pattern.test(password),
  ).length;
  return password.length >= 12 && classes >= 3 ? "strong" : "medium";
}
