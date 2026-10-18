-- AuthService checks for an existing email before saving, but two concurrent sign-ups could still both pass that
-- check; the constraint makes the database the final guard.
ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);

-- Every favorite-location query filters by user.
CREATE INDEX idx_favorite_location_users_id ON favorite_location (users_id);
