"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import signInRequest from "@/app/api/auth/signIn";
import signUpRequest from "@/app/api/auth/signUp";
import AuthCard, { AuthSubmitButton } from "@/app/component/auth/AuthCard";
import AuthField from "@/app/component/auth/AuthField";
import useAuthForm from "@/app/component/auth/useAuthForm";
import { LockIcon, MailIcon, UserIcon } from "@/app/component/home/icons";
import { useToast } from "@/app/component/toast/Toast";
import { useUserDetails } from "@/app/component/userDetails/LoadUserDetails";
import { describeSignUpError } from "../util/authErrors";
import { NICKNAME_MAX_LENGTH, PASSWORD_MIN_LENGTH, validateEmail, validateNickname, validatePassword } from "../util/validation";

function validate(form) {
  return {
    nickname: validateNickname(form.nickname),
    email: validateEmail(form.email),
    password: validatePassword(form.password),
  };
}

export default function SignUp() {
  const router = useRouter();
  const { status, loadUserDetails } = useUserDetails();
  const { showToast } = useToast();
  // The backend's SignUpDto field is `nickname`; any other spelling is silently dropped by Jackson.
  const { values: form, errors, handleChange, handleBlur, validateAll } = useAuthForm(
    { nickname: "", email: "", password: "" },
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
    const credentials = { email: form.email.trim(), password: form.password };
    try {
      await signUpRequest({ nickname: form.nickname.trim(), ...credentials });
    } catch (e) {
      showToast({ variant: "error", title: "Couldn't create your account", message: describeSignUpError(e) });
      setLoading(false);
      return;
    }

    // Sign-up only creates the account; signing in right away sets the session cookies.
    try {
      await signInRequest(credentials);
    } catch {
      showToast({
        variant: "info",
        title: "Your account is ready",
        message: "Sign in with your new email and password to continue.",
      });
      router.push("/sign-in");
      return;
    }

    // Updates the global user details, so the navbar shows the nickname as soon as the home page opens.
    const nickname = await loadUserDetails();
    showToast(
      nickname == null
        ? {
            variant: "info",
            title: "Your account is ready",
            message: "Your details didn't load yet. Refresh the page if you don't see your nickname.",
          }
        : { variant: "success", title: `Welcome, ${nickname}!`, message: "Your account is ready." },
    );
    router.replace("/");
  }

  return (
    <AuthCard
      title="Create your account"
      subtitle="Save your favorite cities and check their weather at a glance."
      footerText="Already have an account?"
      footerLink="Sign in"
      footerHref="/sign-in"
    >
      <form ref={formRef} onSubmit={handleOnSubmit} noValidate className="space-y-5">
        <AuthField
          label="Nickname"
          name="nickname"
          icon={<UserIcon size={18} />}
          autoComplete="nickname"
          placeholder="How should we greet you?"
          maxLength={NICKNAME_MAX_LENGTH + 10}
          value={form.nickname}
          onChange={handleChange}
          onBlur={handleBlur}
          error={errors.nickname}
          disabled={loading}
          required
        />
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
          autoComplete="new-password"
          placeholder="Create a password"
          hint={`At least ${PASSWORD_MIN_LENGTH} characters.`}
          value={form.password}
          onChange={handleChange}
          onBlur={handleBlur}
          error={errors.password}
          disabled={loading}
          required
        />
        <AuthSubmitButton loading={loading} loadingLabel="Creating your account…">
          Create account
        </AuthSubmitButton>
      </form>
    </AuthCard>
  );
}
