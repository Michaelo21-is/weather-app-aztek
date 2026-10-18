"use client";

import { useId, useState } from "react";
import { AlertIcon, EyeIcon, EyeOffIcon } from "../home/icons";

// Labelled glass input in the style of the city search. Password fields get a show/hide toggle.
export default function AuthField({ label, icon, type = "text", error, hint, ...inputProps }) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const isPassword = type === "password";
  const messageId = `${id}-message`;
  const message = error || hint;

  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-white/90">
        {label}
      </label>
      <div className="relative">
        <span className="pointer-events-none absolute inset-y-0 left-4 flex items-center text-white/65">{icon}</span>
        <input
          id={id}
          type={isPassword && visible ? "text" : type}
          aria-invalid={error ? true : undefined}
          aria-describedby={message ? messageId : undefined}
          className={`h-12 w-full rounded-2xl border bg-white/10 pl-11 text-base text-white shadow-lg shadow-black/10 transition-[background-color,border-color,box-shadow] duration-200 placeholder:text-white/50 hover:bg-white/15 focus:bg-white/15 focus:outline-none focus-visible:ring-2 disabled:cursor-not-allowed disabled:opacity-60 ${
            isPassword ? "pr-12" : "pr-4"
          } ${
            error
              ? "border-rose-300/70 focus:border-rose-200 focus-visible:ring-rose-200/60"
              : "border-white/20 focus:border-white/40 focus-visible:ring-white/70"
          }`}
          {...inputProps}
        />
        {isPassword && (
          <div className="absolute inset-y-0 right-1 flex items-center">
            <button
              type="button"
              onClick={() => setVisible((value) => !value)}
              aria-label={visible ? "Hide password" : "Show password"}
              aria-pressed={visible}
              disabled={inputProps.disabled}
              className="flex size-10 cursor-pointer items-center justify-center rounded-xl text-white/70 transition-colors duration-150 hover:bg-white/10 hover:text-white focus-visible:outline-2 focus-visible:outline-white/70 disabled:cursor-not-allowed"
            >
              {visible ? <EyeOffIcon size={18} /> : <EyeIcon size={18} />}
            </button>
          </div>
        )}
      </div>
      {message && (
        <p
          id={messageId}
          className={`mt-1.5 flex items-start gap-1.5 text-sm leading-snug ${error ? "text-rose-200" : "text-white/65"}`}
        >
          {error && <AlertIcon size={15} className="mt-px shrink-0" />}
          {message}
        </p>
      )}
    </div>
  );
}
