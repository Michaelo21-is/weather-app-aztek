// Helpers for the backend's WeatherResponse: times are UTC instants and location.utcOffsetSeconds gives local time.

// Instants arrive as ISO strings; epoch seconds are accepted too in case Jackson is set to write timestamps.
export function toDate(instant) {
  if (instant == null) return null;
  return new Date(typeof instant === "number" ? instant * 1000 : instant);
}

// Shifts an instant into the location's local time. Format the result with timeZone "UTC".
export function toLocationTime(instant, utcOffsetSeconds) {
  const date = toDate(instant);
  return date && new Date(date.getTime() + utcOffsetSeconds * 1000);
}

// LocalDate arrives as "YYYY-MM-DD" (or [year, month, day] when written as a timestamp).
export function toIsoDate(localDate) {
  if (Array.isArray(localDate)) {
    const [year, month, day] = localDate;
    return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  }
  return localDate;
}

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit", timeZone: "UTC" });
const hourFormat = new Intl.DateTimeFormat(undefined, { hour: "numeric", timeZone: "UTC" });
const longDateFormat = new Intl.DateTimeFormat(undefined, {
  weekday: "long",
  day: "numeric",
  month: "long",
  timeZone: "UTC",
});
const weekdayFormat = new Intl.DateTimeFormat(undefined, { weekday: "short", timeZone: "UTC" });

export function formatLocalTime(instant, utcOffsetSeconds) {
  const date = toLocationTime(instant, utcOffsetSeconds);
  return date ? timeFormat.format(date) : "—";
}

export function formatLocalHour(instant, utcOffsetSeconds) {
  return hourFormat.format(toLocationTime(instant, utcOffsetSeconds));
}

export function formatLocalLongDate(instant, utcOffsetSeconds) {
  return longDateFormat.format(toLocationTime(instant, utcOffsetSeconds));
}

export function formatWeekday(isoDate) {
  return weekdayFormat.format(new Date(`${isoDate}T00:00:00Z`));
}

export function localIsoDate(instant, utcOffsetSeconds) {
  return toLocationTime(instant, utcOffsetSeconds).toISOString().slice(0, 10);
}

export function formatDuration(totalMinutes) {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = Math.round(totalMinutes % 60);
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

export function formatTemperature(value) {
  // Math.round(-0.4) is -0, which would render as "-0°".
  return `${Math.round(value) || 0}°`;
}

export function formatPercent(probability) {
  return `${Math.round(probability * 100)}%`;
}

// weather.units is the OpenWeather unit system the backend requested.
const UNIT_SYSTEMS = {
  metric: { temperature: "C", windToMetersPerSecond: 1 },
  imperial: { temperature: "F", windToMetersPerSecond: 0.44704 },
  standard: { temperature: "K", windToMetersPerSecond: 1 },
};

export function unitSystem(units) {
  return UNIT_SYSTEMS[units] ?? UNIT_SYSTEMS.metric;
}

export function formatWind(speed, units) {
  if (units === "imperial") return { value: Math.round(speed), unit: "mph" };
  return { value: Math.round(speed * 3.6), unit: "km/h" };
}

const BEAUFORT_LIMITS = [0.5, 1.6, 3.4, 5.5, 8, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7];
const BEAUFORT_NAMES = [
  "Calm",
  "Light air",
  "Light breeze",
  "Gentle breeze",
  "Moderate breeze",
  "Fresh breeze",
  "Strong breeze",
  "Near gale",
  "Gale",
  "Strong gale",
  "Storm",
  "Violent storm",
  "Hurricane force",
];

export function describeWind(speed, units) {
  const metersPerSecond = speed * unitSystem(units).windToMetersPerSecond;
  const force = BEAUFORT_LIMITS.findIndex((limit) => metersPerSecond < limit);
  return BEAUFORT_NAMES[force === -1 ? BEAUFORT_NAMES.length - 1 : force];
}

export function describeHumidity(humidity) {
  if (humidity < 30) return "Dry air";
  if (humidity < 60) return "Comfortable";
  if (humidity < 80) return "Humid";
  return "Very humid";
}

export function describeFeelsLike(temperature, feelsLike) {
  const difference = feelsLike - temperature;
  if (Math.abs(difference) < 1.5) return "Similar to the actual temperature";
  return difference < 0 ? "Feels cooler than it is" : "Feels warmer than it is";
}

export function capitalize(text) {
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : "";
}

let regionNames = null;

export function countryName(code) {
  if (!code) return null;
  try {
    regionNames ??= new Intl.DisplayNames(undefined, { type: "region" });
    return regionNames.of(code) ?? code;
  } catch {
    return code;
  }
}

// Picks the sky backdrop from the OpenWeather icon code: "01".."50" is the condition group, "d"/"n" day or night.
export function weatherTheme(condition) {
  const icon = condition?.icon;
  if (!icon) return "default";
  const night = icon.endsWith("n");
  switch (icon.slice(0, 2)) {
    case "01":
      return night ? "clear-night" : "clear-day";
    case "02":
    case "03":
    case "04":
      return night ? "clouds-night" : "clouds-day";
    case "09":
    case "10":
      return "rain";
    case "11":
      return "storm";
    case "13":
      return "snow";
    case "50":
      return "mist";
    default:
      return "default";
  }
}

export function isAbortError(error) {
  return error?.code === "ERR_CANCELED";
}

// Status codes come from WeatherController validation (400), WeatherService (404) and ExternalApiCall (502/503/504).
export function describeWeatherError(error) {
  if (!error?.response) {
    return "We can't reach the weather service right now. Check your connection and try again.";
  }
  switch (error.response.status) {
    case 400:
      return "That place can't be looked up. Try a different spelling or a nearby city.";
    case 404:
      return "We couldn't find weather for that city. Try a nearby city instead.";
    case 503:
      return "The weather service is busy right now. Please try again in a minute.";
    case 502:
    case 504:
      return "The weather provider isn't responding. Please try again shortly.";
    default:
      return "Something went wrong while loading the weather. Please try again.";
  }
}

// GeolocationPositionError codes: 1 permission denied, 2 position unavailable, 3 timeout.
export function describeLocationError(code) {
  switch (code) {
    case 1:
      return {
        title: "Location access is off",
        message: "Search for a city above, or allow location access in your browser settings and try again.",
      };
    case 3:
      return {
        title: "Finding your location took too long",
        message: "Try again, or search for a city above.",
      };
    case "unsupported":
      return {
        title: "Location isn't available",
        message: "This browser can't share your location. Search for a city above instead.",
      };
    default:
      return {
        title: "We couldn't find your location",
        message: "Try again, or search for a city above.",
      };
  }
}
