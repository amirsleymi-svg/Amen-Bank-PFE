-- ═══════════════════════════════════════════════════════════════════
--  Amen Bank — V6: Remove ROLE_AUDITOR
--  Flyway Migration: V6__remove_auditor_role.sql
--
--  ROLE_AUDITOR is redundant — ADMIN handles all operations.
-- ═══════════════════════════════════════════════════════════════════

-- Remove permissions granted to ROLE_AUDITOR
DELETE rp FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id
WHERE r.name = 'ROLE_AUDITOR';

-- Remove the role itself
DELETE FROM roles WHERE name = 'ROLE_AUDITOR';

-- Update ENUM columns to drop AUDITOR value
ALTER TABLE admins
    MODIFY COLUMN role ENUM('SUPER_ADMIN','ADMIN') NOT NULL DEFAULT 'ADMIN';

ALTER TABLE admin_invitations
    MODIFY COLUMN role ENUM('SUPER_ADMIN','ADMIN') NOT NULL DEFAULT 'ADMIN';
