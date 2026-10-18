import Link from "next/link";
import GlassCard from "../home/GlassCard";
import { SpinnerIcon } from "../home/icons";

// The frosted card both auth forms sit in, with the switch-page link underneath.
export default function AuthCard({ title, subtitle, footerText, footerLink, footerHref, children }) {
  return (
    <div className="w-full max-w-md">
      <GlassCard className="p-6 sm:p-8">
        <h1 className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">{title}</h1>
        <p className="mt-2 text-base leading-relaxed text-white/75">{subtitle}</p>
        <div className="mt-7">{children}</div>
      </GlassCard>
      <p className="mt-6 animate-rise text-center text-sm text-white/75 [animation-delay:80ms]">
        {footerText}{" "}
        <Link
          href={footerHref}
          className="rounded font-semibold text-white underline decoration-white/40 underline-offset-4 transition-colors duration-150 hover:decoration-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
        >
          {footerLink}
        </Link>
      </p>
    </div>
  );
}

// Full-width primary action, matching the white buttons on the home page.
export function AuthSubmitButton({ loading, loadingLabel, children }) {
  return (
    <button
      type="submit"
      disabled={loading}
      className="mt-2 inline-flex h-12 w-full cursor-pointer items-center justify-center gap-2 rounded-2xl bg-white px-5 font-semibold text-slate-900 shadow-lg shadow-black/15 transition-[background-color,transform,opacity] duration-150 hover:bg-sky-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[0.98] disabled:cursor-wait disabled:opacity-80 disabled:active:scale-100"
    >
      {loading && <SpinnerIcon size={18} />}
      {loading ? loadingLabel : children}
    </button>
  );
}
