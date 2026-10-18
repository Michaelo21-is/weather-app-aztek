import GlassCard from "./GlassCard";
import WeatherIcon from "./WeatherIcon";
import { MapPinIcon } from "./icons";
import {
  capitalize,
  countryName,
  formatLocalLongDate,
  formatLocalTime,
  formatTemperature,
  unitSystem,
} from "./weatherFormat";

function formatCoordinates(lat, lon) {
  const latitude = `${Math.abs(lat).toFixed(2)}° ${lat >= 0 ? "N" : "S"}`;
  const longitude = `${Math.abs(lon).toFixed(2)}° ${lon >= 0 ? "E" : "W"}`;
  return `${latitude}, ${longitude}`;
}

// favoriteAction is the save/remove heart for this place, shown beside its name.
export default function CurrentWeather({ weather, today, isCurrentLocation, favoriteAction, className = "" }) {
  const { location, current, units } = weather;
  const offset = location.utcOffsetSeconds;
  const condition = current.condition;
  // name and country are null for coordinates without a nearby place (e.g. at sea).
  const region = [location.state, countryName(location.country)].filter(Boolean).join(", ");

  return (
    <GlassCard className={`relative overflow-hidden p-6 sm:p-8 ${className}`} aria-labelledby="current-heading">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-20 -top-24 size-72 rounded-full bg-white/10 blur-3xl"
      />
      <div className="relative flex items-start justify-between gap-4">
        <div className="min-w-0">
          {isCurrentLocation && (
            <p className="mb-3 inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-white/85">
              <MapPinIcon size={14} />
              Your location
            </p>
          )}
          <div className="flex items-center gap-3">
            <h1 id="current-heading" className="min-w-0 text-3xl font-semibold tracking-tight text-balance text-white sm:text-4xl">
              {location.name ?? "Your location"}
            </h1>
            {favoriteAction}
          </div>
          <p className="mt-1 text-base text-white/80">
            {region || formatCoordinates(location.lat, location.lon)}
          </p>
          <p className="mt-3 text-sm text-white/70">
            {formatLocalLongDate(current.time, offset)} · as of{" "}
            <time dateTime={current.time}>{formatLocalTime(current.time, offset)}</time> local time
          </p>
        </div>
        {condition && (
          <WeatherIcon
            icon={condition.icon}
            size={112}
            animated
            className="-mr-2 -mt-2 shrink-0 animate-float drop-shadow-[0_10px_25px_rgba(0,0,0,0.25)] sm:size-36"
          />
        )}
      </div>

      <div className="relative mt-6 flex flex-wrap items-end gap-x-6 gap-y-3">
        <p className="flex items-start font-extralight leading-none tracking-tighter text-white tabular-nums">
          <span className="text-8xl sm:text-9xl">{formatTemperature(current.temperature)}</span>
          <span className="mt-3 text-2xl font-light text-white/75 sm:text-3xl">{unitSystem(units).temperature}</span>
        </p>
        <div className="pb-2">
          {condition && (
            <p className="text-xl font-medium text-white sm:text-2xl">{capitalize(condition.description)}</p>
          )}
          <p className="mt-1 text-base text-white/80 tabular-nums">
            {today && (
              <>
                H {formatTemperature(today.maxTemperature)} · L {formatTemperature(today.minTemperature)} ·{" "}
              </>
            )}
            Feels like {formatTemperature(current.feelsLike)}
          </p>
        </div>
      </div>
    </GlassCard>
  );
}
