import GlassCard from "./GlassCard";
import { DropletIcon, SunriseIcon, SunsetIcon, ThermometerIcon, UmbrellaIcon, WindIcon } from "./icons";
import {
  describeFeelsLike,
  describeHumidity,
  describeWind,
  formatDuration,
  formatLocalTime,
  formatPercent,
  formatTemperature,
  formatWind,
  toDate,
} from "./weatherFormat";

function Tile({ icon, label, value, unit, caption, children, index }) {
  return (
    <GlassCard
      as="div"
      className="flex min-h-36 flex-col p-4 sm:p-5"
      style={{ animationDelay: `${120 + index * 40}ms` }}
    >
      <dt className="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.14em] text-white/75">
        {icon}
        {label}
      </dt>
      <dd className="mt-3 flex flex-1 flex-col">
        <span className="text-3xl font-light tracking-tight text-white tabular-nums">
          {value}
          {unit && <span className="ml-1 text-base font-normal text-white/75">{unit}</span>}
        </span>
        {children}
        <span className="mt-auto pt-2 text-sm leading-snug text-white/75">{caption}</span>
      </dd>
    </GlassCard>
  );
}

function Meter({ percent }) {
  return (
    <span className="mt-3 block h-1.5 overflow-hidden rounded-full bg-white/15" aria-hidden="true">
      <span
        className="block h-full origin-left rounded-full bg-linear-to-r from-sky-300 to-sky-100"
        style={{ transform: `scaleX(${Math.min(Math.max(percent, 0), 100) / 100})` }}
      />
    </span>
  );
}

function sunCaption(current, kind) {
  const sunrise = toDate(current.sunrise);
  const sunset = toDate(current.sunset);
  if (!sunrise || !sunset) return "Not available here today";
  if (kind === "sunrise") return `${formatDuration((sunset - sunrise) / 60000)} of daylight`;
  const minutesLeft = (sunset - toDate(current.time)) / 60000;
  return minutesLeft > 0 ? `Sunset in ${formatDuration(minutesLeft)}` : "The sun has set";
}

export default function WeatherDetails({ weather, className = "" }) {
  const { current, location, units } = weather;
  const offset = location.utcOffsetSeconds;
  const wind = formatWind(current.windSpeed, units);
  // The nearest 3-hour forecast step; the current-conditions endpoint has no precipitation probability.
  const nextStep = weather.hourly[0];

  const tiles = [
    {
      key: "feels",
      icon: <ThermometerIcon size={16} />,
      label: "Feels like",
      value: formatTemperature(current.feelsLike),
      caption: describeFeelsLike(current.temperature, current.feelsLike),
    },
    {
      key: "humidity",
      icon: <DropletIcon size={16} />,
      label: "Humidity",
      value: current.humidity,
      unit: "%",
      caption: describeHumidity(current.humidity),
      extra: <Meter percent={current.humidity} />,
    },
    {
      key: "wind",
      icon: <WindIcon size={16} />,
      label: "Wind",
      value: wind.value,
      unit: wind.unit,
      caption: describeWind(current.windSpeed, units),
    },
    nextStep && {
      key: "rain",
      icon: <UmbrellaIcon size={16} />,
      label: "Rain chance",
      value: formatPercent(nextStep.precipitationProbability),
      caption: "In the next few hours",
      extra: <Meter percent={nextStep.precipitationProbability * 100} />,
    },
    {
      key: "sunrise",
      icon: <SunriseIcon size={16} />,
      label: "Sunrise",
      value: formatLocalTime(current.sunrise, offset),
      caption: sunCaption(current, "sunrise"),
    },
    {
      key: "sunset",
      icon: <SunsetIcon size={16} />,
      label: "Sunset",
      value: formatLocalTime(current.sunset, offset),
      caption: sunCaption(current, "sunset"),
    },
  ].filter(Boolean);

  return (
    <section aria-labelledby="details-heading" className={className}>
      <h2 id="details-heading" className="sr-only">
        Current conditions
      </h2>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 lg:grid-cols-6">
        {tiles.map(({ key, extra, ...tile }, index) => (
          <Tile key={key} index={index} {...tile}>
            {extra}
          </Tile>
        ))}
      </dl>
    </section>
  );
}
