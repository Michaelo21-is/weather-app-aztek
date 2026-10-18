-- Schema as Hibernate generated it from the entities (Users, FavoriteLocation, RefreshToken).
-- Databases created before Flyway was introduced already contain these tables and are baselined at V1
-- (spring.flyway.baseline-on-migrate), so this script only runs on empty databases.

CREATE SEQUENCE favorite_location_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE jwt_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE users (
    id                    uuid         NOT NULL,
    email                 varchar(255) NOT NULL,
    favorite_city_counter integer      NOT NULL,
    nickname              varchar(255) NOT NULL,
    password              varchar(255) NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id)
);

CREATE TABLE favorite_location (
    id           bigint       NOT NULL,
    city_name    varchar(255) NOT NULL,
    country_name varchar(255) NOT NULL,
    users_id     uuid         NOT NULL,
    CONSTRAINT favorite_location_pkey PRIMARY KEY (id),
    CONSTRAINT fk_favorite_location_users FOREIGN KEY (users_id) REFERENCES users (id)
);

CREATE TABLE refresh_token (
    id              bigint                      NOT NULL,
    expiration_date timestamp(6) with time zone NOT NULL,
    refresh_token   varchar(255)                NOT NULL,
    users_id        uuid,
    CONSTRAINT refresh_token_pkey PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_users UNIQUE (users_id),
    CONSTRAINT fk_refresh_token_users FOREIGN KEY (users_id) REFERENCES users (id)
);
