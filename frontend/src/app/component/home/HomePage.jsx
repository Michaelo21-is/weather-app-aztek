"use client";

import { useEffect, useEffectEvent, useRef, useState } from "react";
import loadWeatherByCordinatesRequest from "@/app/api/weather/loadWeatherByCordinates";
import getWeatherByCityRequest from "@/app/api/weather/getWeatherByCity";
import getUserFavoriteLocationRequest from "@/app/api/weather/getUserFavoriteLocation";
import saveFavoriteLocationRequest from "@/app/api/weather/saveFavoriteLocation";
import removeFavoriteLocationRequest from "@/app/api/weather/removeFavoriteLocation";
import { useToast } from "@/app/component/toast/Toast";
import { useUserDetails } from "@/app/component/userDetails/LoadUserDetails";
import CitySearch from "./CitySearch";
import CurrentWeather from "./CurrentWeather";
import DailyForecast from "./DailyForecast";
import FavoriteButton from "./FavoriteButton";
import FavoriteLocations from "./FavoriteLocations";
import HourlyForecast from "./HourlyForecast";
import SkyBackdrop from "./SkyBackdrop";
import WeatherDetails from "./WeatherDetails";
import WeatherIcon from "./WeatherIcon";
import WeatherSkeleton from "./WeatherSkeleton";
import { AlertIcon, CloseIcon, HeartIcon, LocateIcon, RetryIcon, SearchIcon, SpinnerIcon } from "./icons";
import {
  describeLocationError,
  describeWeatherError,
  isAbortError,
  localIsoDate,
  toIsoDate,
  weatherTheme,
} from "./weatherFormat";
import {
  canSavePlace,
  describeFavoriteError,
  favoriteKey,
  isSameLocation,
  placeFromLocation,
} from "./favoriteLocations";

const GEOLOCATION_OPTIONS = {
  enableHighAccuracy: false,
  timeout: 10_000,
  // A position from the last 10 minutes is accurate enough for weather.
  maximumAge: 600_000,
};

function scrollBehavior() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
}

// Two decimals (~1 km) is plenty for weather, so the backend never receives the precise position.
function roundCoordinate(value) {
  return Math.round(value * 100) / 100;
}

function WeatherView({ weather, isCurrentLocation, favoriteAction }) {
  const todayIso = localIsoDate(weather.current.time, weather.location.utcOffsetSeconds);
  const forecastToday = weather.daily.find((day) => toIsoDate(day.date) === todayIso);
  // Today's forecast only covers the hours still ahead, so the current reading can lie outside it.
  const today = forecastToday && {
    ...forecastToday,
    minTemperature: Math.min(forecastToday.minTemperature, weather.current.temperature),
    maxTemperature: Math.max(forecastToday.maxTemperature, weather.current.temperature),
  };
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
      <CurrentWeather
        weather={weather}
        today={today}
        isCurrentLocation={isCurrentLocation}
        favoriteAction={favoriteAction}
        className="lg:col-span-8"
      />
      <HourlyForecast weather={weather} className="lg:col-span-8" />
      <DailyForecast
        weather={weather}
        today={today}
        todayIso={todayIso}
        className="lg:col-span-4 lg:col-start-9 lg:row-span-2 lg:row-start-1"
      />
      <WeatherDetails weather={weather} className="lg:col-span-12" />
    </div>
  );
}

function StatusPanel({ problem, onRetry, onSearch }) {
  return (
    <div className="mx-auto mt-6 max-w-lg animate-rise rounded-3xl border border-white/15 bg-slate-950/25 p-8 text-center shadow-xl shadow-black/10 backdrop-blur-xl sm:mt-12 sm:p-10">
      <WeatherIcon icon="02d" size={88} className="mx-auto animate-float" />
      <h1 className="mt-5 text-2xl font-semibold tracking-tight text-white">{problem.title}</h1>
      <p className="mt-2 text-base leading-relaxed text-white/80">{problem.message}</p>
      <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
        <button
          type="button"
          onClick={onSearch}
          className="inline-flex h-12 cursor-pointer items-center justify-center gap-2 rounded-2xl bg-white px-5 font-medium text-slate-900 shadow-lg shadow-black/10 transition-[background-color,transform] duration-150 hover:bg-sky-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[0.97]"
        >
          <SearchIcon size={18} />
          Search for a city
        </button>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="inline-flex h-12 cursor-pointer items-center justify-center gap-2 rounded-2xl border border-white/25 px-5 font-medium text-white transition-[background-color,transform] duration-150 hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[0.97]"
          >
            <RetryIcon size={18} />
            Try again
          </button>
        )}
      </div>
    </div>
  );
}

function InlineAlert({ problem, onRetry, onDismiss }) {
  return (
    <div
      role="alert"
      className="mb-4 flex animate-rise items-start gap-3 rounded-2xl border border-rose-200/30 bg-rose-950/40 p-4 text-white shadow-lg backdrop-blur-xl"
    >
      <AlertIcon size={20} className="mt-0.5 shrink-0 text-rose-200" />
      <div className="min-w-0 flex-1">
        <p className="font-medium">{problem.title}</p>
        <p className="mt-0.5 text-sm leading-relaxed text-white/85">{problem.message}</p>
      </div>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex h-10 shrink-0 cursor-pointer items-center gap-1.5 rounded-xl px-3 text-sm font-medium text-white transition-colors duration-150 hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-white"
        >
          <RetryIcon size={16} />
          Retry
        </button>
      )}
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss"
        className="flex size-10 shrink-0 cursor-pointer items-center justify-center rounded-xl text-white/80 transition-colors duration-150 hover:bg-white/10 hover:text-white focus-visible:outline-2 focus-visible:outline-white"
      >
        <CloseIcon size={18} />
      </button>
    </div>
  );
}

export default function HomePage() {
  const [weather, setWeather] = useState(null);
  // { cityName, countryName } of the displayed weather: what saving it as a favorite stores.
  const [place, setPlace] = useState(null);
  const [isCurrentLocation, setIsCurrentLocation] = useState(false);
  // locating: waiting for the browser position · loading: waiting for the backend · settled: nothing pending
  const [status, setStatus] = useState("locating");
  const [pendingLabel, setPendingLabel] = useState("your location");
  // { title, message, retry? }: shown inline above existing weather, or as a panel when there is none.
  const [problem, setProblem] = useState(null);
  const requestIdRef = useRef(0);
  const controllerRef = useRef(null);
  const searchInputRef = useRef(null);
  const { status: authStatus, isLogin, loadUserDetails } = useUserDetails();
  const { showToast } = useToast();
  // The signed-in user's favorites, each { cityName, countryName, weather }. The single source of truth for
  // whether a place is saved: every heart derives its state from this list.
  const [favoriteLocations, setFavoriteLocations] = useState([]);
  // loading · ready · error; only meaningful while signed in.
  const [favoritesStatus, setFavoritesStatus] = useState("loading");
  // favoriteKey()s with a save or remove in flight; their hearts are disabled so repeated clicks send nothing.
  const [pendingFavorites, setPendingFavorites] = useState([]);
  const favoritesRequestIdRef = useRef(0);
  const favoritesSectionRef = useRef(null);

  // Every load (geolocation or city) supersedes the previous one: the old request is aborted and
  // any late result is ignored, so the latest user choice always wins.
  function beginRequest() {
    controllerRef.current?.abort();
    controllerRef.current = new AbortController();
    requestIdRef.current += 1;
    return { id: requestIdRef.current, signal: controllerRef.current.signal };
  }

  function isLatest(id) {
    return id === requestIdRef.current;
  }

  async function loadWeather(id, request, { fromLocation, place, retry }) {
    setStatus("loading");
    try {
      const data = await request();
      if (!isLatest(id)) return;
      setWeather(data);
      // A searched city keeps the name the user picked; a position takes the name OpenWeather gives it.
      setPlace(place ?? placeFromLocation(data.location));
      setIsCurrentLocation(fromLocation);
      setProblem(null);
    } catch (error) {
      if (isAbortError(error) || !isLatest(id)) return;
      setProblem({ title: "Couldn't load the weather", message: describeWeatherError(error), retry });
    }
    setStatus("settled");
  }

  function locateUser() {
    const { id, signal } = beginRequest();
    setPendingLabel("your location");
    setProblem(null);
    if (!("geolocation" in navigator)) {
      setStatus("settled");
      setProblem(describeLocationError("unsupported"));
      return;
    }
    setStatus("locating");
    navigator.geolocation.getCurrentPosition(
      (position) => {
        // The user picked a city while the browser was still locating them.
        if (!isLatest(id)) return;
        const lat = roundCoordinate(position.coords.latitude);
        const lon = roundCoordinate(position.coords.longitude);
        loadWeather(id, () => loadWeatherByCordinatesRequest(lat, lon, { signal }), {
          fromLocation: true,
          retry: locateUser,
        });
      },
      (error) => {
        if (!isLatest(id)) return;
        setStatus("settled");
        setProblem({ ...describeLocationError(error.code), retry: locateUser });
      },
      GEOLOCATION_OPTIONS,
    );
  }

  function selectCity(city) {
    const { id, signal } = beginRequest();
    setPendingLabel(city.name);
    setProblem(null);
    loadWeather(id, () => getWeatherByCityRequest(city.name, city.country, { signal }), {
      fromLocation: false,
      place: { cityName: city.name, countryName: city.country },
      retry: () => selectCity(city),
    });
  }

  function focusSearch() {
    searchInputRef.current?.focus();
  }

  // Each favorite arrives with its current weather, so this one request fills the whole section.
  // Only the latest load may write the list, so a slow response can't overwrite a newer one.
  function fetchFavorites() {
    const id = ++favoritesRequestIdRef.current;
    getUserFavoriteLocationRequest().then(
      (favorites) => {
        if (id !== favoritesRequestIdRef.current) return;
        setFavoriteLocations(favorites);
        setFavoritesStatus("ready");
      },
      (error) => {
        if (id !== favoritesRequestIdRef.current) return;
        setFavoritesStatus("error");
        // The session couldn't be refreshed: re-check it so the page shows the signed-out state.
        if (error.response?.status === 401) loadUserDetails();
      },
    );
  }

  function reloadFavorites() {
    setFavoritesStatus("loading");
    fetchFavorites();
  }

  function setFavoritePending(key, pending) {
    setPendingFavorites((keys) => (pending ? [...keys, key] : keys.filter((pendingKey) => pendingKey !== key)));
  }

  function dropFavorite(key) {
    setFavoriteLocations((favorites) => favorites.filter((favorite) => favoriteKey(favorite) !== key));
  }

  function showFavoriteError(error, title, options) {
    showToast({ variant: "error", title, message: describeFavoriteError(error, options) });
    if (error.response?.status === 401) loadUserDetails();
  }

  async function saveFavorite(placeToSave, placeWeather) {
    const key = favoriteKey(placeToSave);
    if (pendingFavorites.includes(key)) return;
    setFavoritePending(key, true);
    try {
      await saveFavoriteLocationRequest({ cityName: placeToSave.cityName, countryName: placeToSave.countryName });
      // The weather on screen was just loaded, so the new card needs no extra request.
      setFavoriteLocations((favorites) =>
        favorites.some((favorite) => favoriteKey(favorite) === key)
          ? favorites
          : [...favorites, { ...placeToSave, weather: placeWeather }],
      );
      showToast({ variant: "success", title: `Saved ${placeToSave.cityName} to favorites`, duration: 3000 });
    } catch (error) {
      if (error.response?.status === 409) {
        // Saved elsewhere (e.g. another tab) since the list was loaded: reload it to match the backend.
        reloadFavorites();
        showToast({ variant: "info", title: `${placeToSave.cityName} is already in your favorites` });
      } else {
        showFavoriteError(error, `Couldn't save ${placeToSave.cityName}`, { saving: true });
      }
    } finally {
      setFavoritePending(key, false);
    }
  }

  async function removeFavorite(favorite) {
    const key = favoriteKey(favorite);
    if (pendingFavorites.includes(key)) return;
    setFavoritePending(key, true);
    try {
      // The backend matches the saved strings exactly, so send the favorite's own, not the displayed name.
      await removeFavoriteLocationRequest({ cityName: favorite.cityName, countryName: favorite.countryName });
      dropFavorite(key);
      showToast({ variant: "success", title: `Removed ${favorite.cityName} from favorites`, duration: 3000 });
    } catch (error) {
      // 404: already removed (e.g. in another tab), so the list catches up with the backend.
      if (error.response?.status === 404) dropFavorite(key);
      else showFavoriteError(error, `Couldn't remove ${favorite.cityName}`);
    } finally {
      setFavoritePending(key, false);
    }
  }

  // Shows the favorite through the regular city flow, so it gets fresh weather and the full forecast.
  function selectFavorite(favorite) {
    selectCity({ name: favorite.cityName, country: favorite.countryName });
    window.scrollTo({ top: 0, behavior: scrollBehavior() });
  }

  // The section sits below the weather, out of view on most screens.
  function showFavorites() {
    const section = favoritesSectionRef.current;
    if (!section) return;
    section.scrollIntoView({ behavior: scrollBehavior(), block: "start" });
    // Keyboard and screen reader users continue from the section, not from the button.
    section.focus({ preventScroll: true });
  }

  const onSignedIn = useEffectEvent(() => {
    fetchFavorites();
  });

  // Loads the favorites once someone is signed in. Signing out makes a load still in flight be ignored.
  useEffect(() => {
    if (!isLogin) return;
    onSignedIn();
    return () => {
      favoritesRequestIdRef.current += 1;
    };
  }, [isLogin]);

  const onPermissionKnown = useEffectEvent((state, startId) => {
    // The user already chose a city before the permission check finished.
    if (!isLatest(startId)) return;
    if (state === "denied") {
      // The browser would refuse without asking, so don't call getCurrentPosition at all.
      setStatus("settled");
      setProblem({ ...describeLocationError(1), retry: locateUser });
    } else {
      locateUser();
    }
  });

  // Asks for the location once, on first load. Afterwards only the "My location" button asks again.
  useEffect(() => {
    let cancelled = false;
    const startId = requestIdRef.current;
    const permission = navigator.permissions
      ? navigator.permissions.query({ name: "geolocation" }).then(
          (result) => result.state,
          () => "prompt",
        )
      : Promise.resolve("prompt");
    permission.then((state) => {
      if (!cancelled) onPermissionKnown(state, startId);
    });
    return () => {
      cancelled = true;
      controllerRef.current?.abort();
      requestIdRef.current += 1;
    };
  }, []);

  const busy = status !== "settled";
  // Signed out, nothing counts as saved, even if the last session's list is still in state.
  const favorites = isLogin ? favoriteLocations : [];
  // Derived on every render, so the main heart and the cards always agree with favoriteLocations.
  const currentFavorite =
    weather && place ? favorites.find((favorite) => isSameLocation(favorite, place, weather.location)) : null;
  const currentFavoriteKey = currentFavorite ? favoriteKey(currentFavorite) : null;

  function toggleCurrentFavorite() {
    if (!isLogin) {
      showToast({ variant: "info", title: "You need to log in to save favorite locations." });
      return;
    }
    if (currentFavorite) removeFavorite(currentFavorite);
    else saveFavorite(place, weather);
  }

  const favoriteAction = (currentFavorite || canSavePlace(place)) && (
    <FavoriteButton
      saved={Boolean(currentFavorite)}
      // Until the list arrives we can't tell whether this place is saved.
      pending={
        (isLogin && favoritesStatus === "loading") ||
        pendingFavorites.includes(currentFavoriteKey ?? favoriteKey(place))
      }
      placeName={currentFavorite?.cityName ?? place.cityName}
      onClick={toggleCurrentFavorite}
    />
  );
  const theme = weatherTheme(weather?.current.condition);
  let announcement = "";
  if (status === "locating") announcement = "Finding your location…";
  else if (status === "loading") announcement = `Loading weather for ${pendingLabel}…`;
  else if (weather && !problem) announcement = `Showing weather for ${weather.location.name ?? "your location"}.`;

  return (
    <div className="relative isolate flex flex-1 flex-col overflow-x-clip">
      <SkyBackdrop theme={theme} />

      {/* Thin progress bar while a new location loads over existing weather; it shows in the gap above the floating navbar. */}
      <div
        aria-hidden="true"
        className={`fixed inset-x-0 top-0 z-40 h-0.5 overflow-hidden transition-opacity duration-300 ${
          busy && weather ? "opacity-100" : "opacity-0"
        }`}
      >
        <div className="h-full w-1/2 animate-progress bg-linear-to-r from-transparent via-white to-transparent" />
      </div>

      <div className="relative mx-auto flex w-full max-w-6xl flex-1 flex-col px-4 pb-8 pt-5 sm:px-6 sm:pt-8 lg:px-8">
        <header className="relative z-20">
          <div className="flex items-center gap-2">
            <CitySearch onSelect={selectCity} inputRef={searchInputRef} />
            <button
              type="button"
              onClick={locateUser}
              disabled={status === "locating"}
              aria-label="Show weather for my location"
              title="Show weather for my location"
              className="inline-flex h-12 min-w-12 shrink-0 cursor-pointer items-center justify-center gap-2 rounded-2xl border border-white/20 bg-white/10 px-3.5 text-white shadow-lg shadow-black/10 backdrop-blur-xl transition-[background-color,transform] duration-150 hover:bg-white/20 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[0.97] disabled:cursor-wait disabled:opacity-60"
            >
              {status === "locating" ? <SpinnerIcon size={18} /> : <LocateIcon size={18} />}
              <span className="hidden text-sm font-medium md:inline">My location</span>
            </button>
            {/* Hidden until sign-in is known, because the section itself doesn't render before then. */}
            {authStatus !== "loading" && (
              <button
                type="button"
                onClick={showFavorites}
                aria-label={favorites.length > 0 ? `Favorites, ${favorites.length} saved` : "Favorites"}
                title="Go to your favorite locations"
                className="inline-flex h-12 min-w-12 shrink-0 cursor-pointer items-center justify-center gap-2 rounded-2xl border border-white/20 bg-white/10 px-3.5 text-white shadow-lg shadow-black/10 backdrop-blur-xl transition-[background-color,transform] duration-150 hover:bg-white/20 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[0.97]"
              >
                <HeartIcon
                  size={18}
                  filled={favorites.length > 0}
                  className={favorites.length > 0 ? "text-red-400" : undefined}
                />
                <span className="hidden text-sm font-medium md:inline">Favorites</span>
                {favorites.length > 0 && (
                  <span className="rounded-full bg-white/15 px-1.5 text-xs font-semibold tabular-nums">
                    {favorites.length}
                  </span>
                )}
              </button>
            )}
          </div>
        </header>

        <main className="relative z-10 mt-6 flex-1 sm:mt-8" aria-busy={busy}>
          <p className="sr-only" aria-live="polite">
            {announcement}
          </p>

          {weather ? (
            <>
              {problem && (
                <InlineAlert
                  problem={problem}
                  onRetry={problem.retry}
                  onDismiss={() => setProblem(null)}
                />
              )}
              <div
                className={`transition-opacity duration-300 ${busy ? "pointer-events-none opacity-60" : "opacity-100"}`}
              >
                {/* Keyed by place and fetch time so a new result replays the entrance animation. */}
                <WeatherView
                  key={`${weather.location.lat},${weather.location.lon},${weather.fetchedAt}`}
                  weather={weather}
                  isCurrentLocation={isCurrentLocation}
                  favoriteAction={favoriteAction}
                />
              </div>
            </>
          ) : busy ? (
            <>
              <p className="mb-4 flex items-center gap-2 text-sm text-white/80">
                <SpinnerIcon size={16} />
                {status === "locating" ? "Finding your location…" : `Loading weather for ${pendingLabel}…`}
              </p>
              <WeatherSkeleton />
            </>
          ) : (
            problem && <StatusPanel problem={problem} onRetry={problem.retry} onSearch={focusSearch} />
          )}
        </main>

        <FavoriteLocations
          ref={favoritesSectionRef}
          authStatus={authStatus}
          favorites={favorites}
          status={favoritesStatus}
          activeKey={currentFavoriteKey}
          pendingKeys={pendingFavorites}
          onSelect={selectFavorite}
          onRemove={removeFavorite}
          onRetry={reloadFavorites}
        />

        <footer className="relative z-10 mt-10 text-center text-xs text-white/65">
          Weather data by OpenWeather · City search by Open-Meteo (GeoNames, CC BY 4.0)
        </footer>
      </div>
    </div>
  );
}
