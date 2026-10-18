// Spring includes `message` in error bodies only when server.error.include-message is enabled, so every
// status also has its own fallback. Server messages are trusted for 4xx only; 5xx text may be internal.
function serverMessage(error) {
  const { status, data } = error.response;
  const message = data?.message;
  return status < 500 && typeof message === "string" && message.trim() ? message : null;
}

function describe(error, byStatus, fallback) {
  if (!error?.response) {
    return "We can't reach the server. Check your connection and try again.";
  }
  // api.js loads a CSRF token before every POST. If that load fails, the sign-in/up request was never sent,
  // so its status (e.g. 401) says nothing about the email or password.
  if (error.config?.url?.endsWith("/api/csrf")) {
    return "We couldn't start a secure session with the server. Please try again in a moment.";
  }
  return serverMessage(error) ?? byStatus[error.response.status] ?? fallback;
}

// Statuses come from AuthService.signIn (401) and LoginAttemptService (429).
export function describeSignInError(error) {
  return describe(
    error,
    {
      401: "The email or password is incorrect.",
      429: "Too many failed attempts. Wait a few minutes, then try again.",
    },
    "Something went wrong while signing in. Please try again.",
  );
}

// Statuses come from AuthService.signUp (409 duplicate email, 500 failed save).
export function describeSignUpError(error) {
  return describe(
    error,
    {
      400: "Some of your details weren't accepted. Check them and try again.",
      409: "An account with this email already exists. Sign in instead.",
    },
    "We couldn't create your account. Please try again later.",
  );
}
