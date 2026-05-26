-- ============================================================
-- Auth & Identity Service — Database Schema
-- MySQL 8 | utf8mb4_unicode_ci
-- ============================================================

-- ------------------------------------------------------------
-- 0. Database
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS auth_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE auth_db;

-- ------------------------------------------------------------
-- 1. Module 1 — JWT Basic (initial table)
-- ------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    email         VARCHAR(100)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    full_name     VARCHAR(100),
    role          ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN               NOT NULL DEFAULT TRUE,
    created_at    DATETIME              NOT NULL,
    CONSTRAINT pk_users       PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 2. Module 3 — OAuth2 Google additions
-- ------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN google_id     VARCHAR(100) NULL,
    ADD COLUMN auth_provider ENUM('LOCAL', 'GOOGLE') NOT NULL DEFAULT 'LOCAL',
    ADD CONSTRAINT uq_users_google_id UNIQUE (google_id);

-- ------------------------------------------------------------
-- 3. Module 4 — TOTP 2FA additions
-- ------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN totp_secret  VARCHAR(100) NULL,
    ADD COLUMN totp_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN backup_codes TEXT         NULL;

-- ============================================================
-- Complete schema (all modules combined — use when starting fresh)
-- ============================================================
-- CREATE TABLE users (
--     id            BIGINT        NOT NULL AUTO_INCREMENT,
--     email         VARCHAR(100)  NOT NULL,
--     password_hash VARCHAR(255)  NOT NULL,
--     full_name     VARCHAR(100),
--     role          ENUM('USER', 'ADMIN')   NOT NULL DEFAULT 'USER',
--     enabled       BOOLEAN                 NOT NULL DEFAULT TRUE,
--     created_at    DATETIME                NOT NULL,
--
--     -- M3: OAuth2 Google
--     google_id     VARCHAR(100)  NULL,
--     auth_provider ENUM('LOCAL', 'GOOGLE') NOT NULL DEFAULT 'LOCAL',
--
--     -- M4: TOTP 2FA
--     totp_secret   VARCHAR(100)  NULL,
--     totp_enabled  BOOLEAN       NOT NULL DEFAULT FALSE,
--     backup_codes  TEXT          NULL,
--
--     CONSTRAINT pk_users           PRIMARY KEY (id),
--     CONSTRAINT uq_users_email     UNIQUE (email),
--     CONSTRAINT uq_users_google_id UNIQUE (google_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
