-- ============================================================
-- Custom Prefix System - Complete Setup (safe upgrade version)
-- ============================================================

-- Questo script è pensato per essere rieseguito senza errori
-- anche se le tabelle esistono già con una struttura parziale.

-- ------------------------------------------------------------
-- 1. Main user prefixes table
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_prefixes` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `user_id` INT(11) NOT NULL,
    `text` VARCHAR(50) NOT NULL,
    `color` VARCHAR(255) NOT NULL DEFAULT '#FFFFFF',
    `icon` VARCHAR(50) NOT NULL DEFAULT '',
    `effect` VARCHAR(50) NOT NULL DEFAULT '',
    `font` VARCHAR(50) NOT NULL DEFAULT '',
    `catalog_prefix_id` INT(11) NOT NULL DEFAULT 0,
    `display_name` VARCHAR(100) NOT NULL DEFAULT '',
    `points` INT(11) NOT NULL DEFAULT 0,
    `points_type` INT(11) NOT NULL DEFAULT 0,
    `is_custom` TINYINT(1) NOT NULL DEFAULT 1,
    `active` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_active` (`user_id`, `active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 2. Catalog table
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `custom_prefixes_catalog` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `display_name` VARCHAR(100) NOT NULL DEFAULT '',
    `text` VARCHAR(50) NOT NULL,
    `color` VARCHAR(255) NOT NULL DEFAULT '#FFFFFF',
    `icon` VARCHAR(50) NOT NULL DEFAULT '',
    `effect` VARCHAR(50) NOT NULL DEFAULT '',
    `font` VARCHAR(50) NOT NULL DEFAULT '',
    `points` INT(11) NOT NULL DEFAULT 0,
    `points_type` INT(11) NOT NULL DEFAULT 0,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `sort_order` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 3. User visual settings
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_visual_settings` (
    `user_id` INT(11) NOT NULL,
    `display_order` VARCHAR(50) NOT NULL DEFAULT 'icon-prefix-name',
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 4. Prefix settings table
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `custom_prefix_settings` (
    `key_name` VARCHAR(100) NOT NULL,
    `value` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`key_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 5. Blacklist table
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `custom_prefix_blacklist` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `word` VARCHAR(100) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Schema upgrades for existing installations
-- ============================================================

-- ------------------------------------------------------------
-- user_prefixes: add missing columns safely
-- ------------------------------------------------------------

SET @dbname = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'user_prefixes'
              AND COLUMN_NAME = 'font'
        ),
        'SELECT 1',
        'ALTER TABLE `user_prefixes` ADD COLUMN `font` VARCHAR(50) NOT NULL DEFAULT '''' AFTER `effect`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'user_prefixes'
              AND COLUMN_NAME = 'catalog_prefix_id'
        ),
        'SELECT 1',
        'ALTER TABLE `user_prefixes` ADD COLUMN `catalog_prefix_id` INT(11) NOT NULL DEFAULT 0 AFTER `font`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'user_prefixes'
              AND COLUMN_NAME = 'display_name'
        ),
        'SELECT 1',
        'ALTER TABLE `user_prefixes` ADD COLUMN `display_name` VARCHAR(100) NOT NULL DEFAULT '''' AFTER `catalog_prefix_id`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'user_prefixes'
              AND COLUMN_NAME = 'points'
        ),
        'SELECT 1',
        'ALTER TABLE `user_prefixes` ADD COLUMN `points` INT(11) NOT NULL DEFAULT 0 AFTER `display_name`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'user_prefixes'
              AND COLUMN_NAME = 'points_type'
        ),
        'SELECT 1',
        'ALTER TABLE `user_prefixes` ADD COLUMN `points_type` INT(11) NOT NULL DEFAULT 0 AFTER `points`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'user_prefixes'
              AND COLUMN_NAME = 'is_custom'
        ),
        'SELECT 1',
        'ALTER TABLE `user_prefixes` ADD COLUMN `is_custom` TINYINT(1) NOT NULL DEFAULT 1 AFTER `points_type`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- custom_prefixes_catalog: add missing columns safely
-- ------------------------------------------------------------

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'custom_prefixes_catalog'
              AND COLUMN_NAME = 'font'
        ),
        'SELECT 1',
        'ALTER TABLE `custom_prefixes_catalog` ADD COLUMN `font` VARCHAR(50) NOT NULL DEFAULT '''' AFTER `effect`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'custom_prefixes_catalog'
              AND COLUMN_NAME = 'points'
        ),
        'SELECT 1',
        'ALTER TABLE `custom_prefixes_catalog` ADD COLUMN `points` INT(11) NOT NULL DEFAULT 0 AFTER `font`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'custom_prefixes_catalog'
              AND COLUMN_NAME = 'points_type'
        ),
        'SELECT 1',
        'ALTER TABLE `custom_prefixes_catalog` ADD COLUMN `points_type` INT(11) NOT NULL DEFAULT 0 AFTER `points`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'custom_prefixes_catalog'
              AND COLUMN_NAME = 'enabled'
        ),
        'SELECT 1',
        'ALTER TABLE `custom_prefixes_catalog` ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 AFTER `points_type`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @dbname
              AND TABLE_NAME = 'custom_prefixes_catalog'
              AND COLUMN_NAME = 'sort_order'
        ),
        'SELECT 1',
        'ALTER TABLE `custom_prefixes_catalog` ADD COLUMN `sort_order` INT(11) NOT NULL DEFAULT 0 AFTER `enabled`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- Default settings
-- ============================================================
INSERT IGNORE INTO `custom_prefix_settings` (`key_name`, `value`) VALUES
    ('max_length', '15'),
    ('min_rank_to_buy', '1'),
    ('price_credits', '5'),
    ('price_points', '0'),
    ('points_type', '0'),
    ('font_price_credits', '10'),
    ('font_price_points', '0'),
    ('font_points_type', '0');

-- ============================================================
-- Default catalog entries
-- ============================================================
INSERT IGNORE INTO `custom_prefixes_catalog`
(`id`, `display_name`, `text`, `color`, `icon`, `effect`, `font`, `points`, `points_type`, `enabled`, `sort_order`) VALUES
    (1, 'VIP', 'VIP', '#FFD700', '', 'glow', '', 10, 0, 1, 1),
    (2, 'Legend', 'Legend', '#8B5CF6', '', 'discord-neon', '', 15, 0, 1, 2),
    (3, 'Staff Pick', 'Staff', '#3B82F6', '*', 'cartoon', '', 20, 0, 1, 3);

-- ============================================================
-- Example blacklist entries
-- ============================================================
INSERT IGNORE INTO `custom_prefix_blacklist` (`word`) VALUES
    ('admin'),
    ('staff'),
    ('mod'),
    ('owner');

-- ============================================================
-- Notes
-- ============================================================
-- Preset prefixes for `:customize` are loaded directly by
-- UserNickIconsComposer and displayed inside the `:customize` panel.
--
-- This setup does not require rows in `catalog_pages`.
--
-- Command texts / permission inserts are intentionally omitted
-- for compatibility with both legacy and normalized permission schemas.