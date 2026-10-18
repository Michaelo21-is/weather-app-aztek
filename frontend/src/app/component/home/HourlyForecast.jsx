import GlassCard, { CardTitle } from "./GlassCard";
import WeatherIcon from "./WeatherIcon";
import { DropletIcon } from "./icons";
import { capitalize, formatLocalHour, formatPercent, formatTemperature } from "./weatherFormat";

// The backend forecast is in 3-hour steps; 8 steps cover the next 24 hours.
const STEPS = 8;

export default function HourlyForecast({ weather, className = "" }) {
  const steps = weather.hourly.slice(0, STEPS);
  const offset = weather.location.utcOffsetSeconds;
  if (steps.length === 0) return null;

  return (
    <GlassCard className={`flex flex-col p-5 sm:p-6 ${className}`} style={{ animationDelay: "80ms" }} aria-labelledby="hourly-heading">
      <CardTitle id="hourly-heading">Next 24 hours</CardTitle>
      <ol className="-mx-2 mt-4 flex snap-x sm:my-auto gap-1 overflow-x-auto px-2 pb-1 [scrollbar-width:thin] sm:grid sm:grid-cols-8 sm:overflow-visible">
        {steps.map((step) => (
          <li
            key={step.time}
            className="flex min-w-18 shrink-0 snap-start flex-col items-center gap-2 rounded-2xl px-2 py-3 transition-colors duration-200 hover:bg-white/10 sm:min-w-0"
          >
            <time dateTime={step.time} className="text-sm font-medium text-white/80">
              {formatLocalHour(step.time, offset)}
            </time>
            <WeatherIcon
              icon={step.condition?.icon}
              label={capitalize(step.condition?.description) || "Unknown conditions"}
              size={40}
            />
            <span className="text-lg font-medium text-white tabular-nums">{formatTemperature(step.temperature)}</span>
            <span
              className={`flex items-center gap-0.5 text-xs font-medium tabular-nums ${
                step.precipitationProbability >= 0.1 ? "text-sky-200" : "invisible"
              }`}
              aria-hidden={step.precipitationProbability < 0.1}
            >
              <DropletIcon size={12} />
              <span className="sr-only">Chance of rain </span>
              {formatPercent(step.precipitationProbability)}
            </span>
          </li>
        ))}
      </ol>
    </GlassCard>
  );
}
