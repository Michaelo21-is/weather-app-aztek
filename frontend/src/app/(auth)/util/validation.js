import { EMAIL_REGEX } from "./regex";

// Mirrors SignInDto/SignUpDto. The backend doesn't run @Valid on these requests, so this is the only check.
export const PASSWORD_MIN_LENGTH = 6;
export const NICKNAME_MAX_LENGTH = 30;

// Each validator returns an error message, or "" when the value is fine.
export function validateEmail(email) {
  const value = email.trim();
  if (!value) return "Enter your email address.";
  if (!EMAIL_REGEX.test(value)) return "Enter a valid email address, like name@example.com.";
  return "";
}

export function validatePassword(password, { checkLength = true } = {}) {
  if (!password) return "Enter your password.";
  if (checkLength && password.length < PASSWORD_MIN_LENGTH) {
    return `Use at least ${PASSWORD_MIN_LENGTH} characters.`;
  }
  return "";
}

// The users.nickname column is NOT NULL, so an empty nickname would fail on the server with a 500.
export function validateNickname(nickname) {
  const value = nickname.trim();
  if (!value) return "Choose a nickname.";
  if (value.length > NICKNAME_MAX_LENGTH) return `Use ${NICKNAME_MAX_LENGTH} characters or fewer.`;
  return "";
}
