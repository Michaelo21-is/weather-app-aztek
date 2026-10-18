// Placeholder with the same grid as WeatherView so the layout doesn't jump when the data arrives.
function Block({ className = "" }) {
  return <div className={`animate-shimmer rounded-xl bg-white/15 ${className}`} />;
}

function Surface({ className = "", children }) {
  return (
    <div className={`rounded-3xl border border-white/10 bg-slate-950/20 backdrop-blur-xl ${className}`}>{children}</div>
  );
}

export default function WeatherSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-12" aria-hidden="true">
      <Surface className="p-6 sm:p-8 lg:col-span-8">
        <Block className="h-9 w-48" />
        <Block className="mt-3 h-5 w-36" />
        <Block className="mt-4 h-4 w-64 max-w-full" />
        <div className="mt-8 flex items-end gap-6">
          <Block className="h-24 w-40 sm:h-32 sm:w-52" />
          <div className="space-y-2 pb-2">
            <Block className="h-6 w-32" />
            <Block className="h-5 w-44" />
          </div>
        </div>
      </Surface>
      <Surface className="p-6 lg:col-span-4 lg:row-span-2">
        <Block className="h-4 w-28" />
        <div className="mt-5 space-y-5">
          {Array.from({ length: 6 }, (_, index) => (
            <Block key={index} className="h-7" />
          ))}
        </div>
      </Surface>
      <Surface className="p-6 lg:col-span-8">
        <Block className="h-4 w-28" />
        <div className="mt-5 grid grid-cols-4 gap-3 sm:grid-cols-8">
          {Array.from({ length: 8 }, (_, index) => (
            <Block key={index} className={`h-28 ${index >= 4 ? "max-sm:hidden" : ""}`} />
          ))}
        </div>
      </Surface>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 lg:col-span-12 lg:grid-cols-6">
        {Array.from({ length: 6 }, (_, index) => (
          <Surface key={index} className="h-36 p-5">
            <Block className="h-3 w-20" />
            <Block className="mt-4 h-8 w-16" />
          </Surface>
        ))}
      </div>
    </div>
  );
}
