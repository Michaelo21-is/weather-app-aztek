import { useId } from "react";

const SUN = "#fcd34d";
const SUN_CORE = "#fbbf24";
const MOON = "#e2e8f0";
const CLOUD = "#f8fafc";
const CLOUD_BACK = "#cbd5e1";
const STORM_CLOUD = "#94a3b8";
const RAIN = "#7dd3fc";
const BOLT = "#fde047";

// A cloud spanning roughly x 8–57, y 12–46 of the 64×64 view box.
const CLOUD_PATH = "M18 46a10 10 0 0 1 0-20a14 14 0 0 1 26.5-4a12 12 0 0 1 1.5 24Z";

function Sun({ cx, cy, r, spin }) {
  const rays = Array.from({ length: 8 }, (_, index) => {
    const angle = (index * Math.PI) / 4;
    const cos = Math.cos(angle);
    const sin = Math.sin(angle);
    return {
      x1: (cx + cos * (r + r * 0.45)).toFixed(2),
      y1: (cy + sin * (r + r * 0.45)).toFixed(2),
      x2: (cx + cos * (r + r * 0.85)).toFixed(2),
      y2: (cy + sin * (r + r * 0.85)).toFixed(2),
    };
  });
  return (
    <g>
      <g
        className={spin ? "animate-spin-slow" : undefined}
        style={{ transformOrigin: `${cx}px ${cy}px` }}
        stroke={SUN}
        strokeWidth={Math.max(2, r * 0.26)}
        strokeLinecap="round"
      >
        {rays.map((ray, index) => (
          <line key={index} {...ray} />
        ))}
      </g>
      <circle cx={cx} cy={cy} r={r} fill={SUN_CORE} />
    </g>
  );
}

function Moon({ cx, cy, r }) {
  // useId output may contain characters that are not valid in url(#...) references.
  const maskId = `moon${useId().replace(/[^a-zA-Z0-9_-]/g, "")}`;
  return (
    <g>
      <mask id={maskId}>
        <rect width="64" height="64" fill="white" />
        <circle cx={cx + r * 0.55} cy={cy - r * 0.45} r={r * 0.82} fill="black" />
      </mask>
      <circle cx={cx} cy={cy} r={r} fill={MOON} mask={`url(#${maskId})`} />
    </g>
  );
}

function Cloud({ x = 0, y = 0, scale = 1, fill = CLOUD }) {
  return <path d={CLOUD_PATH} fill={fill} transform={`translate(${x} ${y}) scale(${scale})`} />;
}

function Rain({ xs, color = RAIN }) {
  return (
    <g stroke={color} strokeWidth="3" strokeLinecap="round">
      {xs.map((x) => (
        <line key={x} x1={x} y1="48" x2={x - 3} y2="56" />
      ))}
    </g>
  );
}

function CelestialBehind({ night, spin }) {
  return night ? <Moon cx={21} cy={21} r={10} /> : <Sun cx={22} cy={21} r={8} spin={spin} />;
}

function Glyph({ code, night, spin }) {
  switch (code) {
    case "01":
      return night ? <Moon cx={30} cy={33} r={17} /> : <Sun cx={32} cy={32} r={12} spin={spin} />;
    case "02":
      return (
        <>
          <CelestialBehind night={night} spin={spin} />
          <Cloud x={12} y={14} scale={0.85} />
        </>
      );
    case "03":
      return <Cloud y={4} />;
    case "04":
      return (
        <>
          <Cloud x={12} y={-2} scale={0.8} fill={CLOUD_BACK} />
          <Cloud x={-2} y={10} scale={0.95} />
        </>
      );
    case "09":
      return (
        <>
          <Cloud y={-4} />
          <Rain xs={[23, 33, 43]} />
        </>
      );
    case "10":
      return (
        <>
          <CelestialBehind night={night} spin={spin} />
          <Cloud x={10} y={4} scale={0.8} />
          <Rain xs={[28, 38, 48]} />
        </>
      );
    case "11":
      return (
        <>
          <Cloud y={-4} fill={STORM_CLOUD} />
          <path d="M34 38 26 51h6l-3 10 11-15h-6l3-8Z" fill={BOLT} />
        </>
      );
    case "13":
      return (
        <>
          <Cloud y={-4} />
          <g fill={CLOUD}>
            <circle cx="22" cy="50" r="2.4" />
            <circle cx="33" cy="54" r="2.4" />
            <circle cx="44" cy="50" r="2.4" />
            <circle cx="27" cy="59" r="2.4" />
            <circle cx="39" cy="60" r="2.4" />
          </g>
        </>
      );
    case "50":
      return (
        <g stroke={CLOUD_BACK} strokeWidth="3.5" strokeLinecap="round">
          <line x1="12" y1="22" x2="52" y2="22" />
          <line x1="8" y1="31" x2="44" y2="31" />
          <line x1="18" y1="40" x2="56" y2="40" />
          <line x1="12" y1="49" x2="46" y2="49" />
        </g>
      );
    default:
      return <Cloud y={4} />;
  }
}

/**
 * Draws an OpenWeather icon code ("10d", "01n", ...). Pass `label` when the icon is the only description
 * of the condition; otherwise it is hidden from assistive technology.
 */
export default function WeatherIcon({ icon, label, size = 48, animated = false, className }) {
  const code = icon?.slice(0, 2);
  const night = icon?.endsWith("n") ?? false;
  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      className={className}
      {...(label ? { role: "img", "aria-label": label } : { "aria-hidden": true })}
    >
      <Glyph code={code} night={night} spin={animated} />
    </svg>
  );
}
