// Takes the client as a parameter because api.js calls this from its own interceptor (no circular import).
// GET /api/csrf returns { headerName, token } and makes the backend set the matching HttpOnly XSRF-TOKEN cookie.
export default async function loadCsrfRequest(client) {
  const { data } = await client.get("/api/csrf");
  return data;
}
