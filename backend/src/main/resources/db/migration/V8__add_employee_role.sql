-- ═══════════════════════════════════════════════════════════════════
--  Amen Bank — V8: Add EMPLOYEE Role for Bank Staff
--  Flyway Migration: V8__add_employee_role.sql
--
--  - Adds EMPLOYEE to admins and admin_invitations ENUM
--  - Makes refresh_tokens.user_id nullable to support admin sessions
--  - Adds admin_id FK to refresh_tokens for admin/employee session storage
--  - Inserts ROLE_EMPLOYEE with limited operational permissions
-- ═══════════════════════════════════════════════════════════════════

-- ─── 1. Add EMPLOYEE to admins ENUM ──────────────────────────────────
ALTER TABLE admins
    MODIFY COLUMN role ENUM('ADMIN', 'EMPLOYEE') NOT NULL DEFAULT 'ADMIN';

-- ─── 2. Add EMPLOYEE to admin_invitations ENUM ───────────────────────
ALTER TABLE admin_invitations
    MODIFY COLUMN role ENUM('ADMIN', 'EMPLOYEE') NOT NULL DEFAULT 'ADMIN';

-- ─── 3. Make refresh_tokens.user_id nullable (admin sessions have no user) ─
ALTER TABLE refresh_tokens
    DROP FOREIGN KEY fk_rt_user;

ALTER TABLE refresh_tokens
    MODIFY COLUMN user_id BIGINT NULL;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- ─── 4. Add admin_id FK to refresh_tokens ────────────────────────────
ALTER TABLE refresh_tokens
    ADD COLUMN admin_id BIGINT NULL AFTER user_id;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_rt_admin FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE CASCADE;

ALTER TABLE refresh_tokens
    ADD INDEX idx_rt_admin (admin_id);

-- ─── 5. Add failed_login_attempts and locked_until to admins ─────────
ALTER TABLE admins
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0 AFTER active,
    ADD COLUMN locked_until TIMESTAMP NULL AFTER failed_login_attempts;

-- ─── 6. Insert ROLE_EMPLOYEE ──────────────────────────────────────────
INSERT INTO roles (name) VALUES ('ROLE_EMPLOYEE');

-- ─── 7. Grant ROLE_EMPLOYEE limited operational permissions ───────────
--        Employees can view, review KYC/credits, but cannot approve/manage
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_EMPLOYEE'
  AND p.name IN (
    'ACCOUNT_READ',
    'TRANSACTION_READ',
    'TRANSACTION_EXPORT',
    'CREDIT_REVIEW',
    'KYC_REVIEW',
    'USER_READ',
    'ADMIN_READ',
    'AUDIT_READ',
    'NOTIFICATION_READ',
    'CHATBOT_ADMIN'
  );
