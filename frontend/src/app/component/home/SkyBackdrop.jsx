// One full-screen layer per theme; only the active one is opaque, so a condition change crossfades the sky.
const THEMES = {
  default: {
    sky: "linear-gradient(165deg, #0b1530 0%, #1d2b5c 55%, #3b4a8a 100%)",
    glows: ["#6366f1", "#0ea5e9"],
  },
  "clear-day": {
    sky: "linear-gradient(165deg, #0a4596 0%, #1a66c4 50%, #3f8bd8 100%)",
    glows: ["#93c5fd", "#38bdf8"],
  },
  "clear-night": {
    sky: "linear-gradient(165deg, #040a1c 0%, #0e1b42 55%, #22306a 100%)",
    glows: ["#6366f1", "#1e3a8a"],
  },
  "clouds-day": {
    sky: "linear-gradient(165deg, #2c4866 0%, #4b6785 50%, #708aa3 100%)",
    glows: ["#e2e8f0", "#7dd3fc"],
  },
  "clouds-night": {
    sky: "linear-gradient(165deg, #0b1222 0%, #1c273c 55%, #33405b 100%)",
    glows: ["#64748b", "#312e81"],
  },
  rain: {
    sky: "linear-gradient(165deg, #111e30 0%, #233c57 55%, #395874 100%)",
    glows: ["#38bdf8", "#1e40af"],
  },
  storm: {
    sky: "linear-gradient(165deg, #0a0c18 0%, #201c38 50%, #3a3159 100%)",
    glows: ["#a78bfa", "#facc15"],
  },
  snow: {
    sky: "linear-gradient(165deg, #2a405c 0%, #4c6686 50%, #7890aa 100%)",
    glows: ["#f8fafc", "#bae6fd"],
  },
  mist: {
    sky: "linear-gradient(165deg, #262f3c 0%, #465263 50%, #6a7585 100%)",
    glows: ["#cbd5e1", "#94a3b8"],
  },
};

export default function SkyBackdrop({ theme }) {
  const glows = (THEMES[theme] ?? THEMES.default).glows;
  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 overflow-hidden">
      {Object.entries(THEMES).map(([name, { sky }]) => (
        <div
          key={name}
          className={`absolute inset-0 transition-opacity duration-1000 ease-in-out ${
            name === theme ? "opacity-100" : "opacity-0"
          }`}
          style={{ backgroundImage: sky }}
        />
      ))}
      {/* Two shared ambient glows; their colour transitions with the theme. */}
      <div
        className="absolute -left-[15%] -top-[25%] size-[60vmax] animate-drift rounded-full opacity-30 blur-[120px] transition-colors duration-1000"
        style={{ backgroundColor: glows[0] }}
      />
      <div
        className="absolute -bottom-[30%] -right-[20%] size-[55vmax] animate-drift rounded-full opacity-20 blur-[120px] transition-colors duration-1000 [animation-delay:-12s]"
        style={{ backgroundColor: glows[1] }}
      />
      {/* Darkens the bottom edge so footer text stays readable on light skies. */}
      <div className="absolute inset-x-0 bottom-0 h-1/3 bg-linear-to-t from-slate-950/40 to-transparent" />
    </div>
  );
}
