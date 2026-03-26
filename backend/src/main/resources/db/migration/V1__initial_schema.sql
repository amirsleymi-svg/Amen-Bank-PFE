-- ═══════════════════════════════════════════════════════════════════
--  Amen Bank — V1: Initial Schema
--  Flyway Migration: V1__initial_schema.sql
-- ═══════════════════════════════════════════════════════════════════

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ─── ENUM helper tables (status lookups) ─────────────────────────────

-- ─── ROLES ───────────────────────────────────────────────────────────
CREATE TABLE roles (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50)  NOT NULL UNIQUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── PERMISSIONS ─────────────────────────────────────────────────────
CREATE TABLE permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL UNIQUE COMMENT 'e.g. ACCOUNT_READ, TRANSFER_CREATE',
    description VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── ROLE ↔ PERMISSION (M:N) ─────────────────────────────────────────
CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles(id)       ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── IDENTITY VERIFICATION RECORDS ───────────────────────────────────
CREATE TABLE identity_verification (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    id_card_number VARCHAR(30)  NOT NULL UNIQUE,
    first_name    VARCHAR(100)  NOT NULL,
    last_name     VARCHAR(100)  NOT NULL,
    date_of_birth DATE          NOT NULL,
    nationality   VARCHAR(60)   NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_id_card (id_card_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── USERS ───────────────────────────────────────────────────────────
CREATE TABLE users (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    username              VARCHAR(50)   NOT NULL UNIQUE,
    email                 VARCHAR(150)  NOT NULL UNIQUE,
    password_hash         VARCHAR(255)  NOT NULL,
    first_name            VARCHAR(100)  NOT NULL,
    last_name             VARCHAR(100)  NOT NULL,
    phone_number          VARCHAR(20),
    id_card_number        VARCHAR(30)   NOT NULL UNIQUE,
    date_of_birth         DATE,
    address               TEXT,
    status                ENUM('PENDING','ACTIVE','SUSPENDED','DELETED') NOT NULL DEFAULT 'PENDING',
    -- 2FA
    totp_secret           VARCHAR(255),
    totp_enabled          BOOLEAN       NOT NULL DEFAULT FALSE,
    -- login security
    failed_login_attempts INT           NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP,
    last_login_at         TIMESTAMP,
    last_login_ip         VARCHAR(45),
    -- email verification
    email_verified        BOOLEAN       NOT NULL DEFAULT FALSE,
    email_token           VARCHAR(255),
    email_token_expires   TIMESTAMP,
    -- password reset
    password_reset_token  VARCHAR(255),
    password_reset_expires TIMESTAMP,
    -- timestamps
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_email         (email),
    INDEX idx_username      (username),
    INDEX idx_status        (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── USER ↔ ROLE (M:N) ───────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── BACKUP CODES (TOTP recovery) ────────────────────────────────────
CREATE TABLE backup_codes (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    code_hash  VARCHAR(255) NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_bc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_bc_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── REFRESH TOKENS ──────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    device_info VARCHAR(255),
    ip_address  VARCHAR(45),
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_rt_user   (user_id),
    INDEX idx_rt_token  (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── ADMINS ──────────────────────────────────────────────────────────
CREATE TABLE admins (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)   NOT NULL UNIQUE,
    email         VARCHAR(150)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    first_name    VARCHAR(100)  NOT NULL,
    last_name     VARCHAR(100)  NOT NULL,
    role          ENUM('SUPER_ADMIN','ADMIN','AUDITOR') NOT NULL DEFAULT 'ADMIN',
    totp_secret   VARCHAR(255),
    totp_enabled  BOOLEAN       NOT NULL DEFAULT FALSE,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_admin_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── ADMIN INVITATION TOKENS ─────────────────────────────────────────
CREATE TABLE admin_invitations (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash    VARCHAR(255) NOT NULL UNIQUE,
    email         VARCHAR(150) NOT NULL,
    role          ENUM('SUPER_ADMIN','ADMIN','AUDITOR') NOT NULL DEFAULT 'ADMIN',
    created_by    BIGINT,
    used          BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at       TIMESTAMP,
    expires_at    TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_created_by FOREIGN KEY (created_by) REFERENCES admins(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── KYC REQUESTS ────────────────────────────────────────────────────
CREATE TABLE kyc_requests (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    status          ENUM('PENDING','REVIEWING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_by     BIGINT,
    reviewed_at     TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_kyc_user        FOREIGN KEY (user_id)     REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT fk_kyc_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES admins(id) ON DELETE SET NULL,
    INDEX idx_kyc_user   (user_id),
    INDEX idx_kyc_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── KYC DOCUMENTS ───────────────────────────────────────────────────
CREATE TABLE kyc_documents (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    kyc_request_id BIGINT        NOT NULL,
    doc_type       ENUM('ID_CARD','PASSPORT','PROOF_OF_ADDRESS','OTHER') NOT NULL,
    file_path      VARCHAR(512)  NOT NULL,
    file_name      VARCHAR(255)  NOT NULL,
    mime_type      VARCHAR(100)  NOT NULL,
    file_size_bytes BIGINT       NOT NULL,
    uploaded_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_kd_kyc FOREIGN KEY (kyc_request_id) REFERENCES kyc_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── ACCOUNTS ────────────────────────────────────────────────────────
CREATE TABLE accounts (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    user_id       BIGINT          NOT NULL,
    account_number VARCHAR(20)    NOT NULL UNIQUE,
    iban          VARCHAR(34)     NOT NULL UNIQUE,
    account_type  ENUM('CHECKING','SAVINGS','CREDIT','INVESTMENT') NOT NULL DEFAULT 'CHECKING',
    currency      CHAR(3)         NOT NULL DEFAULT 'TND',
    balance       DECIMAL(18,3)   NOT NULL DEFAULT 0.000,
    available_balance DECIMAL(18,3) NOT NULL DEFAULT 0.000,
    status        ENUM('ACTIVE','FROZEN','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    daily_limit   DECIMAL(18,3)   NOT NULL DEFAULT 5000.000,
    monthly_limit DECIMAL(18,3)   NOT NULL DEFAULT 50000.000,
    opened_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at     TIMESTAMP,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_acc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_acc_user   (user_id),
    INDEX idx_acc_number (account_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── BENEFICIARIES ───────────────────────────────────────────────────
CREATE TABLE beneficiaries (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    name           VARCHAR(150) NOT NULL,
    iban           VARCHAR(34)  NOT NULL,
    bank_name      VARCHAR(150),
    bank_code      VARCHAR(20),
    is_internal    BOOLEAN      NOT NULL DEFAULT FALSE,
    trusted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ben_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_iban (user_id, iban),
    INDEX idx_ben_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── TRANSACTIONS ─────────────────────────────────────────────────────
CREATE TABLE transactions (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    transaction_ref  VARCHAR(40)   NOT NULL UNIQUE COMMENT 'UUID or custom ref',
    account_id       BIGINT        NOT NULL,
    counterpart_iban VARCHAR(34),
    counterpart_name VARCHAR(150),
    type             ENUM('DEBIT','CREDIT') NOT NULL,
    category         ENUM('TRANSFER','STANDING_ORDER','CREDIT_DISBURSEMENT','CREDIT_REPAYMENT','FEE','INTEREST','DEPOSIT','WITHDRAWAL') NOT NULL,
    amount           DECIMAL(18,3) NOT NULL,
    currency         CHAR(3)       NOT NULL DEFAULT 'TND',
    balance_after    DECIMAL(18,3) NOT NULL,
    label            VARCHAR(255),
    note             TEXT,
    status           ENUM('PENDING','COMPLETED','FAILED','CANCELLED','REVERSED') NOT NULL DEFAULT 'PENDING',
    value_date       DATE          NOT NULL,
    processed_at     TIMESTAMP,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_tx_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    INDEX idx_tx_account (account_id),
    INDEX idx_tx_ref     (transaction_ref),
    INDEX idx_tx_date    (value_date),
    INDEX idx_tx_status  (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── TRANSFERS ───────────────────────────────────────────────────────
CREATE TABLE transfers (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    from_account_id     BIGINT        NOT NULL,
    to_iban             VARCHAR(34)   NOT NULL,
    to_name             VARCHAR(150)  NOT NULL,
    amount              DECIMAL(18,3) NOT NULL,
    currency            CHAR(3)       NOT NULL DEFAULT 'TND',
    label               VARCHAR(255),
    status              ENUM('PENDING','PROCESSING','COMPLETED','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    totp_verified       BOOLEAN       NOT NULL DEFAULT FALSE,
    debit_tx_id         BIGINT,
    credit_tx_id        BIGINT,
    batch_id            BIGINT,
    scheduled_date      DATE,
    processed_at        TIMESTAMP,
    failure_reason      VARCHAR(512),
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_tr_from_acc  FOREIGN KEY (from_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tr_debit_tx  FOREIGN KEY (debit_tx_id)     REFERENCES transactions(id) ON DELETE SET NULL,
    INDEX idx_tr_from_acc (from_account_id),
    INDEX idx_tr_status   (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── BATCH TRANSFER ──────────────────────────────────────────────────
CREATE TABLE transfer_batches (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    file_name      VARCHAR(255),
    total_amount   DECIMAL(18,3) NOT NULL,
    total_count    INT          NOT NULL,
    success_count  INT          NOT NULL DEFAULT 0,
    failure_count  INT          NOT NULL DEFAULT 0,
    status         ENUM('PENDING','PROCESSING','COMPLETED','PARTIAL','FAILED') NOT NULL DEFAULT 'PENDING',
    processed_at   TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_tb_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── STANDING ORDERS ─────────────────────────────────────────────────
CREATE TABLE standing_orders (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    from_account_id BIGINT        NOT NULL,
    to_iban         VARCHAR(34)   NOT NULL,
    to_name         VARCHAR(150)  NOT NULL,
    amount          DECIMAL(18,3) NOT NULL,
    currency        CHAR(3)       NOT NULL DEFAULT 'TND',
    label           VARCHAR(255),
    frequency       ENUM('DAILY','WEEKLY','MONTHLY','QUARTERLY','YEARLY') NOT NULL,
    start_date      DATE          NOT NULL,
    end_date        DATE,
    next_run_date   DATE          NOT NULL,
    last_run_date   DATE,
    status          ENUM('ACTIVE','PAUSED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_so_user     FOREIGN KEY (user_id)        REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_so_from_acc FOREIGN KEY (from_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    INDEX idx_so_user      (user_id),
    INDEX idx_so_next_run  (next_run_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── CREDIT APPLICATIONS ─────────────────────────────────────────────
CREATE TABLE credit_applications (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    amount          DECIMAL(18,3)   NOT NULL,
    duration_months INT             NOT NULL,
    annual_rate     DECIMAL(5,4)    NOT NULL,
    monthly_payment DECIMAL(18,3)   NOT NULL,
    total_cost      DECIMAL(18,3)   NOT NULL,
    purpose         VARCHAR(255),
    credit_type     ENUM('PERSONAL','MORTGAGE','AUTO','BUSINESS','STUDENT') NOT NULL,
    status          ENUM('PENDING','REVIEWING','APPROVED','REJECTED','DISBURSED','CLOSED') NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_by     BIGINT,
    reviewed_at     TIMESTAMP,
    disbursed_at    TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ca_user        FOREIGN KEY (user_id)     REFERENCES users(id)  ON DELETE RESTRICT,
    CONSTRAINT fk_ca_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES admins(id) ON DELETE SET NULL,
    INDEX idx_ca_user   (user_id),
    INDEX idx_ca_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── CREDIT DOCUMENTS ────────────────────────────────────────────────
CREATE TABLE credit_documents (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    application_id   BIGINT       NOT NULL,
    doc_type         ENUM('PAYSLIP','BANK_STATEMENT','TAX_RETURN','PROPERTY_DEED','OTHER') NOT NULL,
    file_path        VARCHAR(512) NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    mime_type        VARCHAR(100) NOT NULL,
    uploaded_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_cd_app FOREIGN KEY (application_id) REFERENCES credit_applications(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── NOTIFICATIONS ───────────────────────────────────────────────────
CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT,
    admin_id   BIGINT,
    type       ENUM('ACCOUNT','TRANSFER','CREDIT','KYC','SECURITY','SYSTEM') NOT NULL,
    title      VARCHAR(255) NOT NULL,
    message    TEXT         NOT NULL,
    read_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notif_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT fk_notif_admin FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE CASCADE,
    INDEX idx_notif_user  (user_id, read_at),
    INDEX idx_notif_admin (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── AUDIT LOGS ──────────────────────────────────────────────────────
CREATE TABLE audit_logs (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    actor_type   ENUM('USER','ADMIN','SYSTEM') NOT NULL,
    actor_id     BIGINT,
    actor_email  VARCHAR(150),
    action       VARCHAR(100) NOT NULL COMMENT 'e.g. USER_LOGIN, TRANSFER_CREATED',
    entity_type  VARCHAR(100),
    entity_id    BIGINT,
    description  TEXT,
    old_value    JSON,
    new_value    JSON,
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(512),
    request_id   VARCHAR(40),
    status       ENUM('SUCCESS','FAILURE') NOT NULL DEFAULT 'SUCCESS',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_al_actor      (actor_type, actor_id),
    INDEX idx_al_action     (action),
    INDEX idx_al_entity     (entity_type, entity_id),
    INDEX idx_al_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── CHATBOT SESSIONS ────────────────────────────────────────────────
CREATE TABLE chatbot_sessions (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(40)  NOT NULL UNIQUE,
    user_id    BIGINT,
    started_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at   TIMESTAMP,
    message_count INT       NOT NULL DEFAULT 0,
    resolved   BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_cs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_cs_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── CHATBOT MESSAGES ────────────────────────────────────────────────
CREATE TABLE chatbot_messages (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    session_id BIGINT        NOT NULL,
    role       ENUM('USER','ASSISTANT') NOT NULL,
    content    TEXT          NOT NULL,
    topic      VARCHAR(100),
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_cm_session FOREIGN KEY (session_id) REFERENCES chatbot_sessions(id) ON DELETE CASCADE,
    INDEX idx_cm_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
