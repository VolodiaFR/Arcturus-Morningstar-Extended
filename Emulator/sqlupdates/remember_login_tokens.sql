ALTER TABLE `users`
    ADD COLUMN IF NOT EXISTS `remember_token_hash` VARCHAR(64) NOT NULL DEFAULT '' AFTER `auth_ticket`;

ALTER TABLE `users`
    ADD COLUMN IF NOT EXISTS `remember_token_expires_at` INT(11) UNSIGNED NOT NULL DEFAULT 0 AFTER `remember_token_hash`;

ALTER TABLE `users`
    ADD INDEX IF NOT EXISTS `idx_users_remember_token_hash` (`remember_token_hash`);
