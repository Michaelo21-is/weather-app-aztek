"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import signInRequest from "@/app/api/auth/signIn";
import AuthCard, { AuthSubmitButton } from "@/app/component/auth/AuthCard";
import AuthField from "@/app/component/auth/AuthField";
import useAuthForm from "@/app/component/auth/useAuthForm";
import { LockIcon, MailIcon } from "@/app/component/home/icons";
import { useToast } from "@/app/component/toast/Toast";
import { useUserDetails } from "@/app/component/userDetails/LoadUserDetails";
import { describeSignInError } from "../util/authErrors";
import { validateEmail, validatePassword } from "../util/validation";

// Existing accounts may predate the length rule, so sign-in only checks that a password was entered.
function validate(form) {
  return {
    email: validateEmail(form.email),
    password: validatePassword(form.password, { checkLength: false }),
  };
}

export default function SignIn() {
  const router = useRouter();
  const { status, loadUserDetails } = useUserDetails();
  const { showToast } = useToast();
  const { values: form, errors, handleChange, handleBlur, validateAll } = useAuthForm(
    { email: "", password: "" },
    validate,
  );
  const [loading, setLoading] = useState(false);
  const formRef = useRef(null);

  // Someone who is already signed in has nothing to do here.
  useEffect(() => {
    if (status === "authenticated" && !loading) router.replace("/");
  }, [status, loading, router]);

  async function handleOnSubmit(event) {
    event.preventDefault();
    const invalidField = validateAll();
    if (invalidField) {
      formRef.current.elements[invalidField].focus();
      return;
    }

    setLoading(true);
    try {
      await signInRequest({ email: form.email.trim(), password: form.password });
    } catch (e) {
      showToast({ variant: "error", title: "Couldn't sign in", message: describeSignInError(e) });
      setLoading(false);
      return;
    }

    // Updates the global user details, so the navbar shows the nickname as soon as the home page opens.
    const nickname = await loadUserDetails();
    if (nickname == null) {
      showToast({
        variant: "error",
        title: "We couldn't load your account",
        message: "You signed in, but your details didn't load. Refresh the page to try again.",
      });
      setLoading(false);
      return;
    }
    showToast({ variant: "success", title: `Welcome back, ${nickname}!` });
    router.replace("/");
  }

  return (
    <AuthCard
      title="Welcome back"
      subtitle="Sign in to keep your favorite cities one tap away."
      footerText="New here?"
      footerLink="Create an account"
      footerHref="/sign-up"
    >
      <form ref={formRef} onSubmit={handleOnSubmit} noValidate className="space-y-5">
        <AuthField
          label="Email"
          name="email"
          type="email"
          icon={<MailIcon size={18} />}
          autoComplete="email"
          placeholder="name@example.com"
          value={form.email}
          onChange={handleChange}
          onBlur={handleBlur}
          error={errors.email}
          disabled={loading}
          required
        />
        <AuthField
          label="Password"
          name="password"
          type="password"
          icon={<LockIcon size={18} />}
          autoComplete="current-password"
          placeholder="Your password"
          value={form.password}
          onChange={handleChange}
          onBlur={handleBlur}
          error={errors.password}
          disabled={loading}
          required
        />
        <AuthSubmitButton loading={loading} loadingLabel="Signing in…">
          Sign in
        </AuthSubmitButton>
      </form>
    </AuthCard>
  );
}
