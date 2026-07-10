ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `background_card_id` INT(11) NOT NULL DEFAULT 0 AFTER `background_overlay_id`;
