-- ============================================================================
-- MySQL initialization. See notes/chapter-7 §8.1.
--
-- The image's own env vars (MYSQL_DATABASE / MYSQL_USER / MYSQL_PASSWORD) create
-- exactly one database and one user. We need TWO databases on this server:
--
--     userservice   <- created by MYSQL_DATABASE, owned by userService
--     authservice   <- created here, owned by authService
--
-- Each service reads and writes only its own schema, so this is still
-- database-per-service — it just avoids running a second MySQL container in
-- development. Splitting them onto separate servers later is a connection
-- string change and nothing more.
--
-- WHEN THIS RUNS
-- Only on FIRST initialization — i.e. only when the mysql-data volume is empty.
-- The entrypoint creates the user from MYSQL_USER *before* running this file, so
-- the GRANT below always has a user to target. If you edit this script,
-- `docker compose down -v` is required for the changes to take effect.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS authservice
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- NOTE: 'appuser' is hardcoded because a .sql file cannot read ${MYSQL_USER}.
-- If you change MYSQL_USER in .env, change it here too (and `down -v`).
-- '%' rather than 'localhost': the app connects from another container, so from
-- MySQL's point of view it is a remote host.
GRANT ALL PRIVILEGES ON authservice.* TO 'appuser'@'%';

FLUSH PRIVILEGES;
