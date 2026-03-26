-- Amen Bank — MySQL initialization
SET GLOBAL time_zone = '+00:00';
SET NAMES utf8mb4;

-- Ensure database uses correct charset
ALTER DATABASE amenbank CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create readonly user for monitoring/reporting
CREATE USER IF NOT EXISTS 'amenbank_readonly'@'%' IDENTIFIED BY 'readonly_pass_change_me';
GRANT SELECT ON amenbank.* TO 'amenbank_readonly'@'%';
FLUSH PRIVILEGES;
