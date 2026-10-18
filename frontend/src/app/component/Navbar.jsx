"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import WeatherIcon from "./home/WeatherIcon";
import { ChevronDownIcon, LogInIcon, LogOutIcon, SpinnerIcon, UserIcon } from "./home/icons";
import { useToast } from "./toast/Toast";
import { useUserDetails } from "./userDetails/LoadUserDetails";

// The nickname's first letter on the app's sky gradient; falls back to a person icon.
function Avatar({ nickname, large = false }) {
  const initial = String(nickname ?? "").trim().charAt(0).toUpperCase();
  return (
    <span
      aria-hidden="true"
      className={`flex shrink-0 items-center justify-center rounded-xl bg-linear-to-br from-sky-400 to-indigo-500 font-semibold text-white shadow-inner shadow-white/20 ${
        large ? "size-10 text-base" : "size-8 text-sm"
      }`}
    >
      {initial || <UserIcon size={large ? 18 : 16} />}
    </span>
  );
}

function UserMenu({ nickname, onSignOut }) {
  const [open, setOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [error, setError] = useState(false);
  const { showToast } = useToast();
  const containerRef = useRef(null);
  const triggerRef = useRef(null);
  const panelId = useId();

  // Closes when a click or keyboard focus lands anywhere outside the menu.
  useEffect(() => {
    if (!open) return;
    function closeIfOutside(event) {
      if (!containerRef.current?.contains(event.target)) setOpen(false);
    }
    document.addEventListener("pointerdown", closeIfOutside);
    document.addEventListener("focusin", closeIfOutside);
    return () => {
      document.removeEventListener("pointerdown", closeIfOutside);
      document.removeEventListener("focusin", closeIfOutside);
    };
  }, [open]);

  function handleKeyDown(event) {
    if (event.key === "Escape" && open) {
      event.preventDefault();
      setOpen(false);
      triggerRef.current?.focus();
    }
  }

  async function handleSignOut() {
    setSigningOut(true);
    setError(false);
    try {
      // On success the auth state clears and this menu unmounts.
      await onSignOut();
      showToast({ variant: "success", title: "You've signed out" });
    } catch {
      setError(true);
      setSigningOut(false);
    }
  }

  return (
    <div ref={containerRef} className="relative min-w-0" onKeyDown={handleKeyDown}>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-controls={panelId}
        title={nickname}
        className={`inline-flex h-11 max-w-full cursor-pointer items-center gap-2.5 rounded-2xl border pl-1.5 pr-3 text-white transition-[background-color,border-color,transform] duration-150 hover:border-white/25 hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[0.97] ${
          open ? "border-white/25 bg-white/15" : "border-white/10 bg-white/5"
        }`}
      >
        <Avatar nickname={nickname} />
        <span className="min-w-0 max-w-28 truncate text-sm font-medium sm:max-w-48">{nickname}</span>
        <ChevronDownIcon
          size={16}
          className={`shrink-0 text-white/70 transition-transform duration-200 ease-out-expo ${open ? "rotate-180" : ""}`}
        />
      </button>

      {/* mt-3.5 clears the bar's 10px inner padding, so the menu floats just below the bar itself. */}
      <div
        id={panelId}
        className={`absolute right-0 top-full mt-3.5 w-64 max-w-[calc(100vw-2rem)] origin-top-right rounded-2xl border border-white/15 bg-slate-900/95 p-1.5 shadow-2xl shadow-black/40 backdrop-blur-2xl transition-[opacity,transform,visibility] duration-200 ease-out-expo ${
          open ? "visible translate-y-0 scale-100 opacity-100" : "invisible -translate-y-1 scale-[0.98] opacity-0"
        }`}
      >
        <div className="flex items-center gap-3 px-2.5 py-2.5">
          <Avatar nickname={nickname} large />
          <p className="min-w-0 text-xs text-white/65">
            Signed in as
            <span className="mt-0.5 block truncate text-sm font-medium text-white">{nickname}</span>
          </p>
        </div>
        <div className="mx-1.5 my-1 h-px bg-white/10" />
        <button
          type="button"
          onClick={handleSignOut}
          disabled={signingOut}
          className="flex min-h-11 w-full cursor-pointer items-center gap-3 rounded-xl px-3 text-left text-white/85 transition-colors duration-150 hover:bg-rose-500/15 hover:text-rose-100 focus-visible:outline-2 focus-visible:outline-white/70 disabled:cursor-wait disabled:opacity-60"
        >
          {signingOut ? <SpinnerIcon size={18} /> : <LogOutIcon size={18} />}
          Sign Out
        </button>
        {error && (
          <p role="alert" className="px-3 pb-2 pt-1 text-sm leading-relaxed text-rose-200">
            Couldn&apos;t sign out. Check your connection and try again.
          </p>
        )}
      </div>
    </div>
  );
}

export default function Navbar() {
  const { status, nickname, signOut } = useUserDetails();

  let account;
  if (status === "loading") {
    // Same footprint as the controls it stands in for, so nothing shifts once the user is known.
    account = (
      <span className="block h-11 w-32 animate-shimmer rounded-2xl bg-white/10">
        <span className="sr-only">Checking sign-in status…</span>
      </span>
    );
  } else if (status === "authenticated") {
    account = <UserMenu nickname={nickname} onSignOut={signOut} />;
  } else {
    account = (
      <Link
        href="/sign-in"
        className="inline-flex h-11 shrink-0 cursor-pointer items-center gap-2 rounded-2xl bg-white px-5 text-sm font-semibold text-slate-900 shadow-lg shadow-black/15 transition-[background-color,transform] duration-150 hover:bg-sky-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[0.97]"
      >
        <LogInIcon size={16} />
        Login
      </Link>
    );
  }

  return (
    // A floating glass bar aligned with the page container. The transparent wrapper ignores clicks, so the
    // gap above the bar never blocks the content scrolling beneath it. z-50 keeps the bar and its menu above
    // each page's own stacking context (e.g. the home sky backdrop).
    <header className="pointer-events-none sticky top-0 z-50 mx-auto w-full max-w-6xl px-4 pt-3 sm:px-6 sm:pt-4 lg:px-8">
      <nav
        aria-label="Main"
        className="pointer-events-auto flex h-16 items-center justify-between gap-4 rounded-3xl border border-white/15 bg-slate-950/35 pl-3 pr-2.5 shadow-xl shadow-black/15 backdrop-blur-xl sm:pl-4"
      >
        <Link
          href="/"
          className="flex shrink-0 items-center gap-2.5 rounded-xl transition-opacity duration-150 hover:opacity-85 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white"
        >
          <WeatherIcon icon="02d" size={36} />
          <span className="text-lg font-semibold tracking-tight text-white">Weather</span>
        </Link>
        <div className="flex min-w-0 items-center justify-end">{account}</div>
      </nav>
    </header>
  );
}
