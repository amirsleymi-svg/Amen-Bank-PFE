-- ═══════════════════════════════════════════════════════════════════
--  Amen Bank — V5: Add optimistic-lock version column to accounts
--  Flyway Migration: V5__add_version_to_accounts.sql
--
--  The Account JPA entity uses @Version for optimistic locking.
--  This column was missing from the initial schema, causing
--  Hibernate ddl-auto: validate to reject the schema on startup.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE accounts
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_at;
