-- ═══════════════════════════════════════════════════════════════════
--  Amen Bank — V3: Registration Onboarding Module
--  Flyway Migration: V3__registration_onboarding.sql
-- ═══════════════════════════════════════════════════════════════════

-- ─── REGISTRATION REQUESTS ────────────────────────────────────────────
-- Clients submit only their email + confirmEmail to start the onboarding process.
-- An admin reviews each request and either creates a user account or rejects it.
CREATE TABLE registration_requests (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    email           VARCHAR(150)  NOT NULL UNIQUE,
    status          ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_by     VARCHAR(150)  COMMENT 'Admin email who processed the request',
    reviewed_at     TIMESTAMP,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_reg_email  (email),
    INDEX idx_reg_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── ACTIVATION TOKENS ────────────────────────────────────────────────
-- After admin approves a request and creates a user (enabled=false),
-- a UUID token is sent by email. The client clicks the link, sets a password,
-- and the account becomes active.
CREATE TABLE activation_tokens (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NOT NULL,
    token       VARCHAR(255)  NOT NULL UNIQUE COMMENT 'UUID token sent to client email',
    expires_at  TIMESTAMP     NOT NULL,
    used        BOOLEAN       NOT NULL DEFAULT FALSE,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_at_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_at_token  (token),
    INDEX idx_at_user   (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add ROLE_CLIENT if not present for onboarding-created accounts
INSERT IGNORE INTO roles (name) VALUES ('ROLE_CLIENT');

-- Grant ROLE_CLIENT standard user permissions
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_CLIENT'
  AND p.name IN (
    'ACCOUNT_READ','TRANSACTION_READ','TRANSACTION_EXPORT',
    'TRANSFER_CREATE','TRANSFER_CANCEL',
    'STANDING_ORDER_CREATE','STANDING_ORDER_CANCEL',
    'CREDIT_SIMULATE','CREDIT_APPLY',
    'KYC_SUBMIT','NOTIFICATION_READ','CHATBOT_USE'
  );
