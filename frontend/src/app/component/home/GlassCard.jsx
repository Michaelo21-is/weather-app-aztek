// Frosted surface used by every weather card. The dark tint keeps white text readable on light skies.
export default function GlassCard({ as: Tag = "section", className = "", style, children, ...props }) {
  return (
    <Tag
      className={`animate-rise rounded-3xl border border-white/15 bg-slate-950/25 shadow-xl shadow-black/10 backdrop-blur-xl ${className}`}
      style={style}
      {...props}
    >
      {children}
    </Tag>
  );
}

export function CardTitle({ icon, children, id }) {
  return (
    <h2 id={id} className="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.14em] text-white/75">
      {icon}
      {children}
    </h2>
  );
}
