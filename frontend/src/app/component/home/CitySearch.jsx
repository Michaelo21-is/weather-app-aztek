"use client";

import { useEffect, useId, useRef, useState } from "react";
import citySuggestionQueryRequest from "@/app/api/weather/citySuggestionQuery";
import { CloseIcon, MapPinIcon, SearchIcon, SpinnerIcon } from "./icons";
import { countryName, isAbortError } from "./weatherFormat";

// Mirrors the validation on GET /api/weather/city-suggestion so invalid input never becomes a 400.
const PLACE_NAME = /^[\p{L}\p{M} .'’-]+$/u;
const MIN_QUERY_LENGTH = 2;
const MAX_QUERY_LENGTH = 60;
const DEBOUNCE_MS = 300;

function normalizeQuery(text) {
  return text.trim().replace(/\s+/g, " ");
}

function HighlightedName({ name, query }) {
  if (!name.toLowerCase().startsWith(query.toLowerCase())) return name;
  return (
    <>
      <span className="font-semibold text-white">{name.slice(0, query.length)}</span>
      {name.slice(query.length)}
    </>
  );
}

/**
 * City autocomplete (WAI-ARIA combobox). Suggestions are `{ name, country }` from the backend, where
 * `country` is the ISO alpha-2 code that GET /api/weather/city expects; `onSelect` receives that object.
 */
export default function CitySearch({ onSelect, inputRef }) {
  const [value, setValue] = useState("");
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState([]);
  // idle | loading | done | error | invalid
  const [status, setStatus] = useState("idle");
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const timerRef = useRef(null);
  const controllerRef = useRef(null);
  const listboxId = useId();

  useEffect(
    () => () => {
      clearTimeout(timerRef.current);
      controllerRef.current?.abort();
    },
    [],
  );

  function cancelPending() {
    clearTimeout(timerRef.current);
    controllerRef.current?.abort();
  }

  function requestSuggestions(nextQuery) {
    timerRef.current = setTimeout(async () => {
      const controller = new AbortController();
      controllerRef.current = controller;
      try {
        const results = await citySuggestionQueryRequest(nextQuery, { signal: controller.signal });
        // A newer keystroke aborted this request; its results are stale.
        if (controller.signal.aborted) return;
        setSuggestions(results);
        setActiveIndex(-1);
        setStatus("done");
      } catch (error) {
        if (isAbortError(error) || controller.signal.aborted) return;
        setSuggestions([]);
        setStatus("error");
      }
    }, DEBOUNCE_MS);
  }

  function handleChange(event) {
    const nextValue = event.target.value;
    const nextQuery = normalizeQuery(nextValue);
    setValue(nextValue);
    setQuery(nextQuery);
    setActiveIndex(-1);
    cancelPending();

    if (nextQuery.length < MIN_QUERY_LENGTH) {
      setSuggestions([]);
      setStatus("idle");
      setOpen(false);
      return;
    }
    setOpen(true);
    if (nextQuery.length > MAX_QUERY_LENGTH || !PLACE_NAME.test(nextQuery)) {
      setSuggestions([]);
      setStatus("invalid");
      return;
    }
    setStatus("loading");
    requestSuggestions(nextQuery);
  }

  function reset() {
    cancelPending();
    setValue("");
    setQuery("");
    setSuggestions([]);
    setStatus("idle");
    setOpen(false);
    setActiveIndex(-1);
  }

  function select(suggestion) {
    reset();
    // Closes the on-screen keyboard on phones so the new weather is visible.
    inputRef.current?.blur();
    onSelect(suggestion);
  }

  function handleKeyDown(event) {
    const count = suggestions.length;
    switch (event.key) {
      case "ArrowDown":
        if (count === 0) return;
        event.preventDefault();
        setOpen(true);
        setActiveIndex((index) => (index + 1) % count);
        break;
      case "ArrowUp":
        if (count === 0) return;
        event.preventDefault();
        setOpen(true);
        setActiveIndex((index) => (index <= 0 ? count - 1 : index - 1));
        break;
      case "Enter": {
        // Enter picks the highlighted city, or the best match when nothing is highlighted.
        const suggestion = suggestions[activeIndex] ?? (status === "done" ? suggestions[0] : null);
        if (open && suggestion) {
          event.preventDefault();
          select(suggestion);
        }
        break;
      }
      case "Escape":
        if (open) {
          event.preventDefault();
          setOpen(false);
          setActiveIndex(-1);
        } else if (value) {
          event.preventDefault();
          reset();
        }
        break;
      case "Tab":
        setOpen(false);
        break;
    }
  }

  function handleBlur(event) {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      setOpen(false);
      setActiveIndex(-1);
    }
  }

  const showSpinner = status === "loading";
  const optionId = (index) => `${listboxId}-option-${index}`;
  let message = null;
  if (status === "invalid") {
    message = "Use letters only, for example “São Paulo” or “St. John's”.";
  } else if (status === "error") {
    message = "Couldn't load suggestions. Check your connection and keep typing to try again.";
  } else if (status === "done" && suggestions.length === 0) {
    message = `No cities start with “${query}”. Check the spelling or try a larger city nearby.`;
  } else if (status === "loading" && suggestions.length === 0) {
    message = "Searching cities…";
  }
  let announcement = "";
  if (status === "done") {
    announcement =
      suggestions.length === 0
        ? "No cities found."
        : `${suggestions.length} ${suggestions.length === 1 ? "city" : "cities"} found. Use the arrow keys to choose.`;
  } else if (message && status !== "loading") {
    announcement = message;
  }

  return (
    <div className="relative w-full sm:max-w-md" onBlur={handleBlur}>
      <label htmlFor={`${listboxId}-input`} className="sr-only">
        Search for a city
      </label>
      <div className="pointer-events-none absolute inset-y-0 left-4 z-10 flex items-center text-white/75">
        <SearchIcon size={18} />
      </div>
      <input
        ref={inputRef}
        id={`${listboxId}-input`}
        type="text"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-activedescendant={open && activeIndex >= 0 ? optionId(activeIndex) : undefined}
        autoComplete="off"
        autoCorrect="off"
        spellCheck={false}
        enterKeyHint="search"
        maxLength={80}
        placeholder="Search for a city"
        value={value}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        onFocus={() => status !== "idle" && setOpen(true)}
        className="h-12 w-full rounded-2xl border border-white/20 bg-white/10 pl-11 pr-12 text-base text-white shadow-lg shadow-black/10 backdrop-blur-xl transition-[background-color,border-color,box-shadow] duration-200 placeholder:text-white/65 hover:bg-white/15 focus:border-white/40 focus:bg-white/15 focus:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
      />
      <div className="absolute inset-y-0 right-1.5 flex items-center">
        {showSpinner ? (
          <span className="flex size-10 items-center justify-center text-white/80">
            <SpinnerIcon size={18} />
          </span>
        ) : value ? (
          <button
            type="button"
            onClick={() => {
              reset();
              inputRef.current?.focus();
            }}
            aria-label="Clear search"
            className="flex size-10 cursor-pointer items-center justify-center rounded-xl text-white/75 transition-colors duration-150 hover:bg-white/10 hover:text-white focus-visible:outline-2 focus-visible:outline-white/70"
          >
            <CloseIcon size={18} />
          </button>
        ) : null}
      </div>

      <div
        className={`absolute inset-x-0 top-full z-30 mt-2 origin-top overflow-hidden rounded-2xl border border-white/15 bg-slate-900/90 shadow-2xl shadow-black/40 backdrop-blur-2xl transition-[opacity,transform] duration-200 ease-out-expo ${
          open && (suggestions.length > 0 || message)
            ? "translate-y-0 scale-100 opacity-100"
            : "pointer-events-none -translate-y-1 scale-[0.98] opacity-0"
        }`}
      >
        <ul id={listboxId} role="listbox" aria-label="City suggestions" className="py-1.5">
          {suggestions.map((suggestion, index) => {
            const active = index === activeIndex;
            return (
              <li
                key={`${suggestion.name}|${suggestion.country}`}
                id={optionId(index)}
                role="option"
                aria-selected={active}
                // Keeps focus in the input so the click selects instead of blurring the combobox first.
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => select(suggestion)}
                onMouseMove={() => activeIndex !== index && setActiveIndex(index)}
                className={`mx-1.5 flex min-h-12 cursor-pointer items-center gap-3 rounded-xl px-3 py-2 text-white/85 transition-colors duration-150 ${
                  active ? "bg-white/15 text-white" : ""
                }`}
              >
                <MapPinIcon size={18} className="shrink-0 text-sky-300" />
                <span className="min-w-0 flex-1 truncate">
                  <HighlightedName name={suggestion.name} query={query} />
                </span>
                <span className="shrink-0 text-sm text-white/65">{countryName(suggestion.country)}</span>
                <span className="shrink-0 rounded-md bg-white/10 px-1.5 py-0.5 font-mono text-xs tracking-wide text-white/80">
                  {suggestion.country}
                </span>
              </li>
            );
          })}
        </ul>
        {message && (
          <p className="flex items-center gap-2 px-4 pb-3.5 pt-2 text-sm leading-relaxed text-white/75">
            {status === "loading" && <SpinnerIcon size={16} className="shrink-0" />}
            {message}
          </p>
        )}
      </div>
      <p className="sr-only" aria-live="polite">
        {open ? announcement : ""}
      </p>
    </div>
  );
}
