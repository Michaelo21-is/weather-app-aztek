"use client";

import { useState } from "react";

/**
 * Form state for the auth pages. `validate(values)` returns { field: message } with "" for valid fields.
 * A field shows its error only after it loses focus (or on submit), then updates live while it is fixed.
 */
export default function useAuthForm(initialValues, validate) {
  const [values, setValues] = useState(initialValues);
  const [touched, setTouched] = useState({});
  const allErrors = validate(values);
  const errors = Object.fromEntries(Object.keys(values).map((name) => [name, touched[name] ? allErrors[name] : ""]));

  function handleChange(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: value }));
  }

  function handleBlur(event) {
    const { name } = event.target;
    setTouched((current) => ({ ...current, [name]: true }));
  }

  // Reveals every error; returns the first invalid field's name (to focus it), or null when the form is valid.
  function validateAll() {
    setTouched(Object.fromEntries(Object.keys(values).map((name) => [name, true])));
    return Object.keys(values).find((name) => allErrors[name]) ?? null;
  }

  return { values, errors, handleChange, handleBlur, validateAll };
}
