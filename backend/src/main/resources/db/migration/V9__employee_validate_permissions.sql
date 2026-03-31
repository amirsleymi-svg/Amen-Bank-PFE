-- ═══════════════════════════════════════════════════════════════════
--  Amen Bank — V9: Employee role — normal bank teller permissions
--  Flyway Migration: V9__employee_validate_permissions.sql
--
--  Employee can ONLY:
--    1. Validate/reject credit applications
--    2. Validate/reject pending transfer operations
--    3. Perform deposits and withdrawals (cash operations)
--    4. View client accounts and transactions (read-only)
--
--  Everything else (KYC, user management, account creation,
--  admin/employee creation, audit, freeze, etc.) is ADMIN-ONLY.
-- ═══════════════════════════════════════════════════════════════════

-- ─── 1. Add new permissions for employee operations ──────────────────
INSERT IGNORE INTO permissions (name, description) VALUES
    ('TRANSFER_VALIDATE', 'Approve or reject pending client transfers'),
    ('CASH_OPERATION',    'Perform deposits and withdrawals at counter');

-- ─── 2. Give ROLE_ADMIN the new permissions (admin has everything) ───
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
  AND p.name IN ('TRANSFER_VALIDATE', 'CASH_OPERATION');

-- ─── 3. Clear ALL existing ROLE_EMPLOYEE permissions ─────────────────
DELETE rp FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id
WHERE r.name = 'ROLE_EMPLOYEE';

-- ─── 4. Set ROLE_EMPLOYEE to exactly these permissions ───────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_EMPLOYEE'
  AND p.name IN (
    -- Read-only: view clients and their accounts/transactions
    'ACCOUNT_READ',
    'USER_READ',
    'TRANSACTION_READ',
    -- Credit validation
    'CREDIT_REVIEW',
    'CREDIT_APPROVE',
    -- Transfer validation
    'TRANSFER_VALIDATE',
    -- Deposit / Withdrawal
    'CASH_OPERATION',
    -- Basic notifications
    'NOTIFICATION_READ'
  );
