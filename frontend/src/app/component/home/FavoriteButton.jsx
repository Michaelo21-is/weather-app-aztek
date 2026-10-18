import { HeartIcon, SpinnerIcon } from "./icons";

const SIZES = {
  md: { button: "size-11 rounded-2xl", icon: 22 },
  sm: { button: "size-10 rounded-xl", icon: 20 },
};

// Heart toggle for one place: filled red when saved. Disabled while its save/remove request is in flight.
export default function FavoriteButton({ saved, pending, placeName, onClick, size = "md", className = "" }) {
  const { button, icon } = SIZES[size];
  const label = saved ? `Remove ${placeName} from favorites` : `Save ${placeName} to favorites`;
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={pending}
      aria-label={label}
      title={label}
      className={`flex shrink-0 cursor-pointer items-center justify-center border backdrop-blur-xl transition-[background-color,border-color,color,transform] duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-90 disabled:cursor-wait ${button} ${
        saved
          ? "border-red-300/30 bg-red-500/15 text-red-500 hover:bg-red-500/25"
          : "border-white/20 bg-white/10 text-white hover:bg-white/20"
      } ${className}`}
    >
      {pending ? (
        <SpinnerIcon size={icon - 2} className="text-white" />
      ) : (
        // Keyed by state so the heart pops when it fills.
        <HeartIcon key={saved ? "saved" : "unsaved"}size={icon} filled={saved} className={saved ? "animate-pop" : undefined} />
      )}
    </button>
  );
}
