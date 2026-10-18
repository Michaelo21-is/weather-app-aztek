"use client";

import { createContext, use, useEffect, useEffectEvent, useRef, useState } from "react";
import { AlertIcon, CheckCircleIcon, CloseIcon, InfoIcon } from "../home/icons";

const ToastContext = createContext(null);

const MAX_TOASTS = 3;
const DEFAULT_DURATION = 5000;
// Matches --animate-sink, so a toast is removed right after its exit animation.
const EXIT_MS = 150;

// Errors use role="alert" so screen readers announce them at once; the rest wait politely.
const VARIANTS = {
  success: { Icon: CheckCircleIcon, iconClass: "text-emerald-300", role: "status" },
  error: { Icon: AlertIcon, iconClass: "text-rose-300", role: "alert" },
  info: { Icon: InfoIcon, iconClass: "text-sky-300", role: "status" },
};

let nextId = 0;

function ToastItem({ toast, onDismiss }) {
  const { Icon, iconClass, role } = VARIANTS[toast.variant] ?? VARIANTS.info;
  // Hovering or focusing a toast pauses its timer, so it never disappears while someone is reading it.
  const [paused, setPaused] = useState(false);
  const remainingRef = useRef(toast.duration);

  const onTimeout = useEffectEvent(() => onDismiss(toast.id));

  useEffect(() => {
    if (paused || toast.leaving || !toast.duration) return;
    const startedAt = Date.now();
    const timer = setTimeout(onTimeout, remainingRef.current);
    return () => {
      clearTimeout(timer);
      remainingRef.current -= Date.now() - startedAt;
    };
  }, [paused, toast.leaving, toast.duration]);

  return (
    <div
      role={role}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      className={`pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl border border-white/15 bg-slate-900/90 py-3 pl-4 pr-2 text-white shadow-2xl shadow-black/40 backdrop-blur-2xl ${
        toast.leaving ? "animate-sink" : "animate-rise"
      }`}
    >
      <Icon size={20} className={`mt-0.5 shrink-0 ${iconClass}`} />
      <div className="min-w-0 flex-1 py-px">
        <p className="font-medium leading-snug">{toast.title}</p>
        {toast.message && <p className="mt-1 text-sm leading-relaxed text-white/80">{toast.message}</p>}
      </div>
      <button
        type="button"
        onClick={() => onDismiss(toast.id)}
        aria-label="Dismiss notification"
        className="-my-1 flex size-10 shrink-0 cursor-pointer items-center justify-center rounded-xl text-white/70 transition-colors duration-150 hover:bg-white/10 hover:text-white focus-visible:outline-2 focus-visible:outline-white/70"
      >
        <CloseIcon size={16} />
      </button>
    </div>
  );
}

/**
 * App-wide notifications. Call `showToast({ variant, title, message, duration })` from `useToast()`;
 * variant is "success" | "error" | "info", and duration 0 keeps the toast until it is dismissed.
 */
export default function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  function dismissToast(id) {
    setToasts((current) => current.map((toast) => (toast.id === id ? { ...toast, leaving: true } : toast)));
    setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), EXIT_MS);
  }

  function showToast({ variant = "info", title, message, duration = DEFAULT_DURATION }) {
    const id = ++nextId;
    // Only the newest few stay on screen; older ones make room instead of stacking up the page.
    setToasts((current) => [
      ...current.filter((toast) => !toast.leaving).slice(-(MAX_TOASTS - 1)),
      { id, variant, title, message, duration },
    ]);
    return id;
  }

  return (
    <ToastContext value={{ showToast, dismissToast }}>
      {children}
      {/* Bottom centre on phones, bottom right on larger screens; above the navbar (z-50). */}
      <section
        aria-label="Notifications"
        className="pointer-events-none fixed inset-x-0 bottom-0 z-60 flex flex-col items-center gap-2 p-4 sm:items-end sm:p-6"
      >
        {toasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} onDismiss={dismissToast} />
        ))}
      </section>
    </ToastContext>
  );
}

export function useToast() {
  const context = use(ToastContext);
  if (!context) {
    throw new Error("useToast must be used inside <ToastProvider>.");
  }
  return context;
}
