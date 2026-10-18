import axios from "axios";
import loadCsrfRequest from "./csrf/loadCsrf";

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_BASE_URL,
  withCredentials: true,
});

// Spring Security only checks the CSRF token on state-changing requests.
const CSRF_METHODS = new Set(["post", "put", "patch", "delete"]);

let csrfPromise = null;

// Loaded on the first state-changing request and shared by concurrent ones; a failed load is retried next time.
function getCsrfToken() {
  csrfPromise ??= loadCsrfRequest(api).catch((error) => {
    csrfPromise = null;
    throw error;
  });
  return csrfPromise;
}

api.interceptors.request.use(async (config) => {
  if (CSRF_METHODS.has(config.method)) {
    const { headerName, token } = await getCsrfToken();
    config.headers.set(headerName, token);
  }
  return config;
});

// A 403 on a state-changing request means the CSRF cookie expired or was replaced: reload the token and retry once.
api.interceptors.response.use(undefined, (error) => {
  const { config, response } = error;
  if (response?.status === 403 && config && CSRF_METHODS.has(config.method) && !config.csrfRetried) {
    csrfPromise = null;
    return api({ ...config, csrfRetried: true });
  }
  return Promise.reject(error);
});

export default api;
