-- Fortune Wheel — admin permission grant (`acc_wheeladmin`)
--
-- Both the client (FortuneWheelView "Settings" button) and the server handlers
-- (WheelAdminGetPrizesEvent / WheelAdminSavePrizesEvent, PERMISSION_KEY =
-- "acc_wheeladmin") gate the prize editor on `acc_wheeladmin`.
--
-- The key itself is registered upstream in
-- `Database Updates/008_soundboard_fortune_wheel.sql`, but that file only grants
-- it to `rank_7` (its author's 7-rank hotel) and its ON DUPLICATE clause touches
-- the comment only. So on a hotel with a different rank layout the key would end
-- up granted to nobody useful. This file supplies the portable grant.
--
-- Idempotent + portable: registers the key as a no-op safety (in case 008 hasn't
-- run yet) and grants it to the same ranks that hold acc_ads_background — the
-- adjacent "manage room-ad furni" permission — with dynamic SQL over the per-rank
-- columns, so no rank ids are hardcoded. If acc_ads_background is absent the JOIN
-- matches nothing and the key stays ungranted (safe; the button just stays
-- hidden until granted by hand).
--
-- After applying, reload at runtime with `:update_permissions` (rebinds online
-- users to the fresh rank and rebroadcasts the permission map — no relog) or
-- restart the emulator.

-- 1) Safety no-op registration (008 is the canonical registrar). rank_* default 0.
INSERT IGNORE INTO `permission_definitions` (`permission_key`, `max_value`, `comment`)
VALUES (
    'acc_wheeladmin',
    1,
    'Allows opening the fortune wheel prize editor (FortuneWheelSettingsView) to add/edit prize slices. Gated server-side by the same key.'
);

-- 2) Grant to the same ranks as acc_ads_background, portably.
SET @cols := NULL;

SELECT GROUP_CONCAT(CONCAT('dst.`', `column_name`, '` = src.`', `column_name`, '`') SEPARATOR ', ')
    INTO @cols
FROM `information_schema`.`columns`
WHERE `table_schema` = DATABASE()
  AND `table_name`   = 'permission_definitions'
  AND `column_name` REGEXP '^rank_[0-9]+$';

SET @sql := CONCAT(
    'UPDATE `permission_definitions` dst ',
    'JOIN `permission_definitions` src ON src.`permission_key` = ''acc_ads_background'' ',
    'SET ', @cols, ' ',
    'WHERE dst.`permission_key` = ''acc_wheeladmin'''
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
