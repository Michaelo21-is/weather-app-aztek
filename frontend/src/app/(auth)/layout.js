import Link from "next/link";
import SkyBackdrop from "@/app/component/home/SkyBackdrop";
import WeatherIcon from "@/app/component/home/WeatherIcon";
import { ArrowLeftIcon } from "@/app/component/home/icons";

// Sign-in and sign-up share the home page's sky, without the navbar: the brand and a back link lead home.
export default function AuthLayout({ children }) {
  return (
    <div className="relative isolate flex min-h-dvh flex-col overflow-x-clip">
      <SkyBackdrop theme="default" />
      <header className="relative z-10 mx-auto flex w-full max-w-6xl items-center justify-between gap-4 px-4 pt-5 sm:px-6 sm:pt-8 lg:px-8">
        <Link
          href="/"
          className="flex shrink-0 items-center gap-2.5 rounded-xl transition-opacity duration-150 hover:opacity-85 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white"
        >
          <WeatherIcon icon="02d" size={36} />
          <span className="text-lg font-semibold tracking-tight text-white">Weather</span>
        </Link>
        <Link
          href="/"
          className="inline-flex h-11 shrink-0 items-center gap-2 rounded-2xl border border-white/15 bg-white/5 px-3.5 text-sm font-medium text-white backdrop-blur-xl transition-[background-color,transform] duration-150 hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[0.97]"
        >
          <ArrowLeftIcon size={16} />
          Back to weather
        </Link>
      </header>
      <main className="relative z-10 flex flex-1 items-center justify-center px-4 py-10 sm:py-16">{children}</main>
    </div>
  );
}
