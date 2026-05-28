-- Soundboard
-- The room flag column + sounds table are also created at boot by
-- SoundboardManager (ALTER ... ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT
-- EXISTS), so applying this file is only needed to seed sounds up-front.

ALTER TABLE `rooms` ADD COLUMN IF NOT EXISTS `soundboard_enabled` TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS `soundboard_sounds` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL DEFAULT '',            -- pad label shown in the client
    `url` VARCHAR(255) NOT NULL DEFAULT '',            -- audio url (uploaded via CMS, like custom badges)
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `sort_order` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
