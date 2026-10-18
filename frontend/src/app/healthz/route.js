// Health check for the hosting platform. It deliberately doesn't call the backend: the backend has its own
// probe, and a backend outage shouldn't get the website restarted.
export function GET() {
  return Response.json({ status: "UP" });
}
