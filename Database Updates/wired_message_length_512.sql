INSERT INTO `wired_emulator_settings` (`key`, `value`, `comment`)
VALUES ('hotel.wired.message.max_length', '512', 'Maximum length of text fields used by wired messages and bot text effects.')
ON DUPLICATE KEY UPDATE `value` = '512';
