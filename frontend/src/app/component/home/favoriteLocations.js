// Helpers for the favorites API: a favorite is { cityName, countryName, weather }, where cityName and countryName
// are exactly what was saved (the backend matches them as-is) and weather is that city's current WeatherResponse.

// The API's identity of a favorite. Remove requests must send these exact strings.
export function favoriteKey({ cityName, countryName }) {
  return `${cityName}|${countryName}`;
}

// What can be saved for the displayed weather: the searched city, or the place OpenWeather names for a position.
export function placeFromLocation(location) {
  return { cityName: location.name, countryName: location.country };
}

// Coordinates without a nearby place (e.g. at sea) have no name to save.
export function canSavePlace(place) {
  return Boolean(place?.cityName && place?.countryName);
}

function normalizeName(name) {
  return name.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().trim();
}

// A searched city and its saved favorite are usually served from the same backend city cache entry, so they share coordinates exactly.
function sameCoordinates(a, b) {
  return Math.abs(a.lat - b.lat) < 1e-4 && Math.abs(a.lon - b.lon) < 1e-4;
}

/**
 * Whether a favorite is the displayed place. Searched cities match on coordinates; positions from geolocation
 * have the visitor's own coordinates, so they match on name (ignoring case and accents) within the same country.
 */
export function isSameLocation(favorite, place, location) {
  const favoriteLocation = favorite.weather?.location;
  if (favoriteLocation && location && sameCoordinates(favoriteLocation, location)) return true;
  if (!canSavePlace(place) || favorite.countryName.toUpperCase() !== place.countryName.toUpperCase()) return false;
  const favoriteNames = new Set([favorite.cityName, favoriteLocation?.name].filter(Boolean).map(normalizeName));
  return [place.cityName, location?.name].filter(Boolean).some((name) => favoriteNames.has(normalizeName(name)));
}

// Status codes come from WeatherService's favorite methods (403 limit, 404 missing, 409 duplicate) and Spring Security (401).
// A 403 on save is the favorites limit; elsewhere it can only be a rejected CSRF token.
export function describeFavoriteError(error, { saving = false } = {}) {
  if (!error?.response) return "Check your connection and try again.";
  const status = error.response.status;
  if (status === 401) return "Your session has expired. Please log in again.";
  if (status === 403 && saving) return "You've reached the limit of saved locations. Remove one to save another.";
  return "Something went wrong. Please try again.";
}
