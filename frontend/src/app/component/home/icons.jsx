// Interface icons in one stroke style (24×24, 2px, round joins). Decorative: label the control, not the icon.
function Icon({ size = 20, className, children }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export function SearchIcon(props) {
  return (
    <Icon {...props}>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </Icon>
  );
}

export function CloseIcon(props) {
  return (
    <Icon {...props}>
      <path d="M18 6 6 18" />
      <path d="m6 6 12 12" />
    </Icon>
  );
}

export function MapPinIcon(props) {
  return (
    <Icon {...props}>
      <path d="M20 10c0 5-5.5 10.2-7.4 11.8a1 1 0 0 1-1.2 0C9.5 20.2 4 15 4 10a8 8 0 0 1 16 0" />
      <circle cx="12" cy="10" r="3" />
    </Icon>
  );
}

export function LocateIcon(props) {
  return (
    <Icon {...props}>
      <path d="M2 12h3" />
      <path d="M19 12h3" />
      <path d="M12 2v3" />
      <path d="M12 19v3" />
      <circle cx="12" cy="12" r="7" />
      <circle cx="12" cy="12" r="3" />
    </Icon>
  );
}

export function ThermometerIcon(props) {
  return (
    <Icon {...props}>
      <path d="M14 4v10.5a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z" />
    </Icon>
  );
}

export function DropletIcon(props) {
  return (
    <Icon {...props}>
      <path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7Z" />
    </Icon>
  );
}

export function WindIcon(props) {
  return (
    <Icon {...props}>
      <path d="M17.7 7.7a2.5 2.5 0 1 1 1.8 4.3H2" />
      <path d="M9.6 4.6A2 2 0 1 1 11 8H2" />
      <path d="M12.6 19.4A2 2 0 1 0 14 16H2" />
    </Icon>
  );
}

export function UmbrellaIcon(props) {
  return (
    <Icon {...props}>
      <path d="M22 12a10 10 0 0 0-20 0Z" />
      <path d="M12 12v8a2 2 0 0 0 4 0" />
      <path d="M12 2v1" />
    </Icon>
  );
}

export function SunriseIcon(props) {
  return (
    <Icon {...props}>
      <path d="M12 2v8" />
      <path d="m8 6 4-4 4 4" />
      <path d="m4.9 10.9 1.4 1.4" />
      <path d="m19.1 10.9-1.4 1.4" />
      <path d="M2 18h2" />
      <path d="M20 18h2" />
      <path d="M16 18a4 4 0 0 0-8 0" />
      <path d="M22 22H2" />
    </Icon>
  );
}

export function SunsetIcon(props) {
  return (
    <Icon {...props}>
      <path d="M12 10V2" />
      <path d="m16 6-4 4-4-4" />
      <path d="m4.9 10.9 1.4 1.4" />
      <path d="m19.1 10.9-1.4 1.4" />
      <path d="M2 18h2" />
      <path d="M20 18h2" />
      <path d="M16 18a4 4 0 0 0-8 0" />
      <path d="M22 22H2" />
    </Icon>
  );
}

export function AlertIcon(props) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="10" />
      <path d="M12 8v4" />
      <path d="M12 16h.01" />
    </Icon>
  );
}

export function RetryIcon(props) {
  return (
    <Icon {...props}>
      <path d="M21 12a9 9 0 1 1-9-9c2.5 0 4.9 1 6.7 2.7L21 8" />
      <path d="M21 3v5h-5" />
    </Icon>
  );
}

export function UserIcon(props) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="8" r="5" />
      <path d="M20 21a8 8 0 0 0-16 0" />
    </Icon>
  );
}

export function ChevronDownIcon(props) {
  return (
    <Icon {...props}>
      <path d="m6 9 6 6 6-6" />
    </Icon>
  );
}

export function LogInIcon(props) {
  return (
    <Icon {...props}>
      <path d="m10 17 5-5-5-5" />
      <path d="M15 12H3" />
      <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
    </Icon>
  );
}

export function LogOutIcon(props) {
  return (
    <Icon {...props}>
      <path d="m16 17 5-5-5-5" />
      <path d="M21 12H9" />
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    </Icon>
  );
}

export function CheckCircleIcon(props) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="10" />
      <path d="m9 12 2 2 4-4" />
    </Icon>
  );
}

export function InfoIcon(props) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="10" />
      <path d="M12 16v-4" />
      <path d="M12 8h.01" />
    </Icon>
  );
}

export function MailIcon(props) {
  return (
    <Icon {...props}>
      <rect width="20" height="16" x="2" y="4" rx="2" />
      <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
    </Icon>
  );
}

export function LockIcon(props) {
  return (
    <Icon {...props}>
      <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </Icon>
  );
}

export function EyeIcon(props) {
  return (
    <Icon {...props}>
      <path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0" />
      <circle cx="12" cy="12" r="3" />
    </Icon>
  );
}

export function EyeOffIcon(props) {
  return (
    <Icon {...props}>
      <path d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49" />
      <path d="M14.084 14.158a3 3 0 0 1-4.242-4.242" />
      <path d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143" />
      <path d="m2 2 20 20" />
    </Icon>
  );
}

export function ArrowLeftIcon(props) {
  return (
    <Icon {...props}>
      <path d="m12 19-7-7 7-7" />
      <path d="M19 12H5" />
    </Icon>
  );
}

export function SpinnerIcon({ size = 20, className = "" }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      className={`animate-spin ${className}`}
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="2.5" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  );
}

// Outlined by default; `filled` paints the inside with the current text color.
export function HeartIcon({ filled = false, ...props }) {
  return (
    <Icon {...props}>
      <path
        d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"
        fill={filled ? "currentColor" : "none"}
      />
    </Icon>
  );
}
