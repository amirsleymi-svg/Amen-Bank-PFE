-- ═══════════════════════════════════════════════════════════════════
--  Amen Bank — V2: Seed Data
--  Flyway Migration: V2__seed_data.sql
-- ═══════════════════════════════════════════════════════════════════

-- ─── Roles ───────────────────────────────────────────────────────────
INSERT INTO roles (name) VALUES
    ('ROLE_USER'),
    ('ROLE_SUPER_ADMIN'),
    ('ROLE_ADMIN'),
    ('ROLE_AUDITOR');

-- ─── Permissions ─────────────────────────────────────────────────────
INSERT INTO permissions (name, description) VALUES
    -- Account
    ('ACCOUNT_READ',        'View own accounts and balances'),
    ('ACCOUNT_CREATE',      'Open new account'),
    ('ACCOUNT_FREEZE',      'Freeze/unfreeze account'),
    ('ACCOUNT_CLOSE',       'Close account'),
    -- Transactions
    ('TRANSACTION_READ',    'View own transactions'),
    ('TRANSACTION_EXPORT',  'Export transactions to CSV'),
    -- Transfers
    ('TRANSFER_CREATE',     'Initiate transfers'),
    ('TRANSFER_CANCEL',     'Cancel pending transfer'),
    -- Standing Orders
    ('STANDING_ORDER_CREATE', 'Create standing order'),
    ('STANDING_ORDER_CANCEL', 'Cancel standing order'),
    -- Credit
    ('CREDIT_SIMULATE',     'Simulate credit offer'),
    ('CREDIT_APPLY',        'Submit credit application'),
    ('CREDIT_REVIEW',       'Review credit applications (admin)'),
    ('CREDIT_APPROVE',      'Approve or reject credits (admin)'),
    -- KYC
    ('KYC_SUBMIT',          'Submit KYC documents'),
    ('KYC_REVIEW',          'Review KYC requests (admin)'),
    ('KYC_APPROVE',         'Approve or reject KYC (admin)'),
    -- Users
    ('USER_READ',           'View user list (admin)'),
    ('USER_MANAGE',         'Manage users (admin)'),
    -- Admin
    ('ADMIN_READ',          'View admin list'),
    ('ADMIN_CREATE',        'Create new admin'),
    ('ADMIN_MANAGE',        'Manage admins'),
    -- Audit
    ('AUDIT_READ',          'View audit logs'),
    -- Notifications
    ('NOTIFICATION_READ',   'View notifications'),
    -- Chatbot
    ('CHATBOT_USE',         'Use the chatbot'),
    ('CHATBOT_ADMIN',       'View chatbot analytics (admin)');

-- ─── Role → Permission mappings ──────────────────────────────────────
-- ROLE_USER gets standard permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_USER'
  AND p.name IN (
    'ACCOUNT_READ','TRANSACTION_READ','TRANSACTION_EXPORT',
    'TRANSFER_CREATE','TRANSFER_CANCEL',
    'STANDING_ORDER_CREATE','STANDING_ORDER_CANCEL',
    'CREDIT_SIMULATE','CREDIT_APPLY',
    'KYC_SUBMIT','NOTIFICATION_READ','CHATBOT_USE'
  );

-- ROLE_AUDITOR gets read-only access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_AUDITOR'
  AND p.name IN (
    'ACCOUNT_READ','TRANSACTION_READ','TRANSACTION_EXPORT',
    'TRANSFER_CREATE',
    'CREDIT_SIMULATE','CREDIT_REVIEW',
    'KYC_REVIEW',
    'USER_READ','ADMIN_READ','AUDIT_READ',
    'NOTIFICATION_READ','CHATBOT_USE','CHATBOT_ADMIN'
  );

-- ROLE_ADMIN gets operational permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
  AND p.name IN (
    'ACCOUNT_READ','ACCOUNT_FREEZE','ACCOUNT_CLOSE',
    'TRANSACTION_READ','TRANSACTION_EXPORT',
    'TRANSFER_CREATE','TRANSFER_CANCEL',
    'STANDING_ORDER_CREATE','STANDING_ORDER_CANCEL',
    'CREDIT_SIMULATE','CREDIT_APPLY','CREDIT_REVIEW','CREDIT_APPROVE',
    'KYC_SUBMIT','KYC_REVIEW','KYC_APPROVE',
    'USER_READ','USER_MANAGE',
    'ADMIN_READ',
    'AUDIT_READ','NOTIFICATION_READ',
    'CHATBOT_USE','CHATBOT_ADMIN'
  );

-- ROLE_SUPER_ADMIN gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_SUPER_ADMIN';

-- ─── Sample Identity Verification Records ────────────────────────────
INSERT INTO identity_verification (id_card_number, first_name, last_name, date_of_birth, nationality) VALUES
    ('12345678', 'Mohamed', 'Ben Ali', '1990-05-15', 'Tunisian'),
    ('87654321', 'Fatma', 'Trabelsi', '1985-09-22', 'Tunisian'),
    ('11223344', 'Ahmed', 'Mansour', '1995-03-10', 'Tunisian'),
    ('44332211', 'Leila', 'Bouazizi', '1988-11-30', 'Tunisian'),
    ('55667788', 'Karim', 'Haddad', '1992-07-04', 'Tunisian');
