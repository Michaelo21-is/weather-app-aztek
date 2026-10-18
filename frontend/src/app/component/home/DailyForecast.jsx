import GlassCard, { CardTitle } from "./GlassCard";
import WeatherIcon from "./WeatherIcon";
import { capitalize, formatPercent, formatTemperature, formatWeekday, toIsoDate } from "./weatherFormat";

// Positions each day's min–max bar on the scale shared by the whole forecast.
function RangeBar({ min, max, low, high, current }) {
  const span = Math.max(high - low, 1);
  const start = ((min - low) / span) * 100;
  const end = ((max - low) / span) * 100;
  return (
    <div className="relative h-1.5 flex-1 rounded-full bg-white/15" aria-hidden="true">
      <div
        className="absolute inset-y-0 rounded-full bg-linear-to-r from-sky-300 via-emerald-200 to-amber-300"
        style={{ left: `${start}%`, right: `${100 - end}%` }}
      />
      {current != null && (
        <div
          className="absolute top-1/2 size-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-slate-900/60 bg-white"
          style={{ left: `${((Math.min(Math.max(current, min), max) - low) / span) * 100}%` }}
        />
      )}
    </div>
  );
}

export default function DailyForecast({ weather, today, todayIso, className = "" }) {
  const days = weather.daily.map((day) => (today && toIsoDate(day.date) === todayIso ? today : day));
  if (days.length === 0) return null;
  const low = Math.min(...days.map((day) => day.minTemperature));
  const high = Math.max(...days.map((day) => day.maxTemperature));

  return (
    <GlassCard
      className={`flex flex-col p-5 sm:p-6 ${className}`}
      style={{ animationDelay: "40ms" }}
      aria-labelledby="daily-heading"
    >
      <CardTitle id="daily-heading">{days.length}-day forecast</CardTitle>
      <ul className="mt-2 flex flex-1 flex-col">
        {days.map((day) => {
          const isoDate = toIsoDate(day.date);
          const isToday = isoDate === todayIso;
          const description = capitalize(day.condition?.description) || "Unknown conditions";
          return (
            <li
              key={isoDate}
              className="flex min-h-14 flex-1 items-center gap-3 border-b border-white/10 py-2 last:border-b-0"
            >
              <span className="w-12 shrink-0 text-base font-medium text-white">
                {isToday ? "Today" : formatWeekday(isoDate)}
              </span>
              <span className="flex w-14 shrink-0 flex-col items-center">
                <WeatherIcon icon={day.condition?.icon} label={description} size={32} />
                {day.precipitationProbability >= 0.2 && (
                  <span className="text-[11px] font-semibold leading-none text-sky-200 tabular-nums">
                    <span className="sr-only">Chance of rain </span>
                    {formatPercent(day.precipitationProbability)}
                  </span>
                )}
              </span>
              <span className="w-10 shrink-0 text-right text-base text-white/70 tabular-nums">
                <span className="sr-only">Low </span>
                {formatTemperature(day.minTemperature)}
              </span>
              <RangeBar
                min={day.minTemperature}
                max={day.maxTemperature}
                low={low}
                high={high}
                current={isToday ? weather.current.temperature : null}
              />
              <span className="w-10 shrink-0 text-base font-medium text-white tabular-nums">
                <span className="sr-only">High </span>
                {formatTemperature(day.maxTemperature)}
              </span>
            </li>
          );
        })}
      </ul>
      <p className="mt-3 text-xs leading-relaxed text-white/65">
        The first and last days cover only the hours left in the 5-day forecast.
      </p>
    </GlassCard>
  );
}
