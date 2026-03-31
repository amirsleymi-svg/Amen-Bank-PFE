-- ═══════════════════════════════════════════════════════════════════
--  Amen Bank — V4: Add password_hash to registration_requests
--  Flyway Migration: V4__add_password_hash_to_registration_requests.sql
-- ═══════════════════════════════════════════════════════════════════
--  Clients now submit email + password during the registration request.
--  The password is hashed (BCrypt) and stored here until the admin
--  creates the user account from this request.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE registration_requests
    ADD COLUMN password_hash VARCHAR(255) NOT NULL DEFAULT '' AFTER email;
