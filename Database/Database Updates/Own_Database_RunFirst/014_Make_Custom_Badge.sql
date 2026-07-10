-- Make sure that the emulator has write access to the badge_path folder !!!!!

CREATE TABLE IF NOT EXISTS `users_custom_badge_settings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `badge_path` varchar(255) NOT NULL DEFAULT '/var/www/gamedata/c_images/album1584',
  `badge_url` varchar(255) NOT NULL DEFAULT '/gamedata/c_images/album1584',
  `price_badge` int(11) NOT NULL DEFAULT 0,
  `currency_type` int(11) NOT NULL DEFAULT -1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

INSERT INTO `users_custom_badge_settings` (`id`, `badge_path`, `badge_url`, `price_badge`, `currency_type`)
SELECT 1, '/var/www/gamedata/c_images/album1584', '/gamedata/c_images/album1584', 50, 5
WHERE NOT EXISTS (SELECT 1 FROM `users_custom_badge_settings` WHERE `id` = 1);

CREATE TABLE IF NOT EXISTS `user_custom_badge` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `badge_id` varchar(64) NOT NULL,
  `badge_name` varchar(64) NOT NULL DEFAULT '',
  `badge_description` varchar(255) NOT NULL DEFAULT '',
  `date_created` int(11) NOT NULL DEFAULT 0,
  `date_edit` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `badge_id` (`badge_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `fk_user_custom_badge_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;