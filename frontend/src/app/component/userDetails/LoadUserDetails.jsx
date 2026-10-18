"use client";

import { createContext, use, useEffect, useEffectEvent, useRef, useState } from "react";
import getNicknameRequest from "@/app/api/user/getNickname";
import signOutRequest from "@/app/api/auth/signOut";

const UserDetailsContext = createContext(null);

/**
 * Loads the signed-in user once, when the app first mounts, and shares it with every page.
 * It lives in the root layout, which persists across navigations, so the request isn't repeated per page.
 * After a successful sign-in, `await loadUserDetails()` from `useUserDetails()` to pick up the new session.
 */
export default function LoadUserDetails({ children }) {
  const [nickname, setNickname] = useState(null);
  // loading: first check still pending · authenticated · unauthenticated
  const [status, setStatus] = useState("loading");
  const requestIdRef = useRef(0);

  // Resolves with the nickname, or null when nobody is signed in.
  function loadUserDetails() {
    // Only the latest load (or sign-out) may write the state, so a slow response can't overwrite a newer one.
    const id = ++requestIdRef.current;
    return getNicknameRequest().then(
      (responseData) => {
        if (id === requestIdRef.current) {
          setNickname(responseData);
          setStatus("authenticated");
        }
        return responseData;
      },
      () => {
        if (id === requestIdRef.current) {
          setNickname(null);
          setStatus("unauthenticated");
        }
        return null;
      },
    );
  }

  // Throws when the request fails, so the caller can keep the user signed in and show an error.
  async function signOut() {
    await signOutRequest();
    requestIdRef.current += 1;
    setNickname(null);
    setStatus("unauthenticated");
  }

  const onMount = useEffectEvent(() => {
    loadUserDetails();
  });

  useEffect(() => {
    onMount();
  }, []);

  const value = {
    nickname,
    status,
    isLogin: status === "authenticated",
    loadUserDetails,
    signOut,
  };

  return <UserDetailsContext value={value}>{children}</UserDetailsContext>;
}

export function useUserDetails() {
  const context = use(UserDetailsContext);
  if (!context) {
    throw new Error("useUserDetails must be used inside <LoadUserDetails>.");
  }
  return context;
}
