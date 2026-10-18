import Link from "next/link";
import FavoriteButton from "./FavoriteButton";
import GlassCard from "./GlassCard";
import WeatherIcon from "./WeatherIcon";
import { AlertIcon, HeartIcon, LogInIcon, RetryIcon } from "./icons";
import { favoriteKey } from "./favoriteLocations";
import { capitalize, countryName, formatTemperature, unitSystem } from "./weatherFormat";

const GRID = "grid grid-cols-1 gap-3 sm:grid-cols-2 sm:gap-4 lg:grid-cols-4";

function FavoriteCard({ favorite, active, pending, index, onSelect, onRemove }) {
  const { weather } = favorite;
  const condition = weather?.current.condition;
  const region = [weather?.location.state, countryName(favorite.countryName)].filter(Boolean).join(", ");
  return (
    <GlassCard
      as="li"
      className={`relative transition-colors duration-200 ${active ? "border-white/45 bg-white/10" : ""}`}
      // A short stagger so the cards arrive in reading order.
      style={{ animationDelay: `${Math.min(index, 8) * 40}ms` }}
    >
      {/* The heart is a sibling, not a child, so removing never also selects the card. */}
      <button
        type="button"
        onClick={() => onSelect(favorite)}
        aria-current={active ? "true" : undefined}
        className="flex h-full w-full cursor-pointer flex-col rounded-3xl p-5 text-left transition-colors duration-150 hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:bg-white/15"
      >
        <span className="min-w-0 pr-12">
          <span className="block truncate text-lg font-semibold tracking-tight text-white" title={favorite.cityName}>
            {favorite.cityName}
          </span>
          <span className="block truncate text-sm text-white/75">{region}</span>
          {active && <span className="sr-only">(currently shown)</span>}
        </span>
        {weather ? (
          <span className="mt-4 flex items-end justify-between gap-3">
            <span className="min-w-0">
              <span className="flex items-start font-extralight leading-none tracking-tighter text-white tabular-nums">
                <span className="text-4xl">{formatTemperature(weather.current.temperature)}</span>
                <span className="mt-1 text-base font-light text-white/70">{unitSystem(weather.units).temperature}</span>
              </span>
              {condition && (
                <span className="mt-2 block truncate text-sm text-white/80">{capitalize(condition.description)}</span>
              )}
            </span>
            {condition && (
              <WeatherIcon
                icon={condition.icon}
                size={56}
                className="-mb-1 -mr-1 shrink-0 drop-shadow-[0_6px_14px_rgba(0,0,0,0.25)]"
              />
            )}
          </span>
        ) : (
          <span className="mt-4 text-sm text-white/70">Weather unavailable right now</span>
        )}
      </button>
      <FavoriteButton
        saved
        pending={pending}
        placeName={favorite.cityName}
        onClick={() => onRemove(favorite)}
        size="sm"
        className="absolute right-3 top-3"
      />
    </GlassCard>
  );
}

function Notice({ icon, title, children }) {
  return (
    <GlassCard as="div" className="flex flex-col items-center px-6 py-10 text-center">
      <span className="flex size-12 items-center justify-center rounded-2xl bg-white/10 text-white">{icon}</span>
      <p className="mt-4 font-medium text-white">{title}</p>
      {children}
    </GlassCard>
  );
}

/**
 * The signed-in user's saved places with their current weather. Selecting a card shows it in the main view;
 * `activeKey` is the favorite currently shown there and `pendingKeys` the favorites with a request in flight.
 */
export default function FavoriteLocations({
  ref,
  authStatus,
  favorites,
  status,
  activeKey,
  pendingKeys,
  onSelect,
  onRemove,
  onRetry,
}) {
  // Nothing to say until we know whether someone is signed in.
  if (authStatus === "loading") return null;

  let content;
  if (authStatus !== "authenticated") {
    content = (
      <Notice icon={<HeartIcon size={24} />} title="Keep your places one tap away">
        <p className="mt-1 max-w-sm text-sm leading-relaxed text-white/75">
          Log in to save favorite locations and see their weather at a glance.
        </p>
        <Link
          href="/sign-in"
          className="mt-5 inline-flex h-11 cursor-pointer items-center gap-2 rounded-2xl bg-white px-5 text-sm font-semibold text-slate-900 shadow-lg shadow-black/15 transition-[background-color,transform] duration-150 hover:bg-sky-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[0.97]"
        >
          <LogInIcon size={16} />
          Login
        </Link>
      </Notice>
    );
  } else if (status === "error" && favorites.length === 0) {
    content = (
      <div
        role="alert"
        className="flex animate-rise items-center gap-3 rounded-2xl border border-rose-200/30 bg-rose-950/40 p-4 text-white shadow-lg backdrop-blur-xl"
      >
        <AlertIcon size={20} className="shrink-0 text-rose-200" />
        <p className="min-w-0 flex-1 text-sm leading-relaxed">
          <span className="font-medium">Couldn&apos;t load your favorite locations.</span>{" "}
          <span className="text-white/85">Check your connection and try again.</span>
        </p>
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex h-10 shrink-0 cursor-pointer items-center gap-1.5 rounded-xl px-3 text-sm font-medium text-white transition-colors duration-150 hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-white"
        >
          <RetryIcon size={16} />
          Retry
        </button>
      </div>
    );
  } else if (status !== "ready" && favorites.length === 0) {
    // Same footprint as the cards, so the section doesn't jump when they arrive.
    content = (
      <div className={GRID} aria-hidden="true">
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} className="h-36 rounded-3xl border border-white/10 bg-slate-950/20 p-5 backdrop-blur-xl">
            <div className="h-5 w-28 animate-shimmer rounded-lg bg-white/15" />
            <div className="mt-2 h-4 w-20 animate-shimmer rounded-lg bg-white/15" />
            <div className="mt-5 h-9 w-16 animate-shimmer rounded-lg bg-white/15" />
          </div>
        ))}
      </div>
    );
  } else if (favorites.length === 0) {
    content = (
      <Notice icon={<HeartIcon size={24} />} title="No favorite locations yet">
        <p className="mt-1 max-w-sm text-sm leading-relaxed text-white/75">
          Tap the heart next to a city&apos;s name to save it here.
        </p>
      </Notice>
    );
  } else {
    content = (
      <ul className={GRID}>
        {favorites.map((favorite, index) => {
          const key = favoriteKey(favorite);
          return (
            <FavoriteCard
              key={key}
              favorite={favorite}
              index={index}
              active={key === activeKey}
              pending={pendingKeys.includes(key)}
              onSelect={onSelect}
              onRemove={onRemove}
            />
          );
        })}
      </ul>
    );
  }

  const loading = authStatus === "authenticated" && status === "loading";
  return (
    // Focusable so the header's Favorites button can move focus here; scroll-mt clears the sticky navbar.
    <section
      ref={ref}
      tabIndex={-1}
      aria-labelledby="favorites-heading"
      aria-busy={loading}
      className="relative z-10 mt-10 scroll-mt-28 outline-none"
    >
      <h2 id="favorites-heading" className="mb-4 flex items-center gap-2.5 text-xl font-semibold tracking-tight text-white">
        Favorite Locations
        {favorites.length > 0 && (
          <span className="rounded-full bg-white/10 px-2.5 py-0.5 text-sm font-medium text-white/80 tabular-nums">
            {favorites.length}
          </span>
        )}
      </h2>
      {loading && <p className="sr-only">Loading your favorite locations…</p>}
      {content}
    </section>
  );
}
