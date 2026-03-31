-- ═══════════════════════════════════════════════════════════════════
--  Amen Bank — V7: Remove ROLE_SUPER_ADMIN
--  Flyway Migration: V7__remove_super_admin_role.sql
--
--  ROLE_SUPER_ADMIN is removed — ROLE_ADMIN now holds all permissions.
-- ═══════════════════════════════════════════════════════════════════

-- ─── 1. Grant ROLE_ADMIN any permissions it didn't already have ──────
--        (ACCOUNT_CREATE, ADMIN_CREATE, ADMIN_MANAGE were SUPER_ADMIN-only)
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
  AND p.name IN ('ACCOUNT_CREATE', 'ADMIN_CREATE', 'ADMIN_MANAGE');

-- ─── 2. Migrate any existing SUPER_ADMIN admins to ADMIN ────────────
UPDATE admins SET role = 'ADMIN' WHERE role = 'SUPER_ADMIN';

-- ─── 3. Remove ROLE_SUPER_ADMIN permissions ──────────────────────────
DELETE rp FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id
WHERE r.name = 'ROLE_SUPER_ADMIN';

-- ─── 4. Remove the ROLE_SUPER_ADMIN role itself ──────────────────────
DELETE FROM roles WHERE name = 'ROLE_SUPER_ADMIN';

-- ─── 5. Drop SUPER_ADMIN from ENUM on admins table ───────────────────
ALTER TABLE admins
    MODIFY COLUMN role ENUM('ADMIN') NOT NULL DEFAULT 'ADMIN';

-- ─── 6. Drop SUPER_ADMIN from ENUM on admin_invitations table ────────
ALTER TABLE admin_invitations
    MODIFY COLUMN role ENUM('ADMIN') NOT NULL DEFAULT 'ADMIN';
