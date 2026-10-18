import Navbar from "@/app/component/Navbar";

// Every page outside the (auth) group gets the navbar; sign-in and sign-up render without it.
// Pages fill the rest of the viewport through flex-1, so the navbar never adds a scrollbar.
export default function MainLayout({ children }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <Navbar />
      <div className="flex flex-1 flex-col">{children}</div>
    </div>
  );
}
