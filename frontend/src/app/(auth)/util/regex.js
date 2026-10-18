// One "@", no spaces, and a dot in the domain: stricter than the backend's @Email, which accepts "a@b".
export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
