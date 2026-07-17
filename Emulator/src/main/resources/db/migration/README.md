# Polaris database migrations

Flyway migrations. `V1` is the **Arcturus Morningstar 3.5.5 baseline**; every
Polaris change is `V2…Vn`. Fresh installs run `V1` then the deltas; Arc and
existing-Polaris installs are baselined at `V1` (skipped) and run the deltas.

## Rules

1. **One logical change per migration.** Never edit a released migration —
   forward-fix with a new version. Flyway records each version on success and
   never re-runs it.
2. **Versions:** `V1` = Arc baseline. Deltas start at `V2`. Flyway treats `V1`
   and `V001` as the same version, so do not reuse `1`.
3. **MariaDB only.** Native `IF [NOT] EXISTS` is allowed (and preferred) for
   additive changes; it is not valid on MySQL 8.
4. **Guard for adoption.** A migration may meet a database that already has the
   object (an Arc converter, or a hand-applied existing-Polaris DB). Guard so it
   is a safe no-op there.
5. **Destructive / table-rebuilding steps** (`DROP`, engine change, `MODIFY` on a
   large table) document lock/runtime/space/recovery and are separately reviewed.
   MariaDB DDL is not rollback-safe — recovery is forward-fix or restore.
6. **Java migrations are exceptional.** Use one only where the source schema is
   genuinely dynamic. `V5` uses Flyway's supported Java-migration API to retain
   custom legacy permission columns; ordinary schema and data changes remain SQL.

## Patterns

### 1. Additive, name is trustworthy — MariaDB one-liner

```sql
CREATE TABLE IF NOT EXISTS `wired_emulator_settings` ( ... ) ENGINE=InnoDB;
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `access_token_version` BIGINT NOT NULL DEFAULT 0;
ALTER TABLE `users` ADD INDEX IF NOT EXISTS `idx_users_mail` (`mail`);
```

### 2. Might already exist with a different definition — state-aware

`IF NOT EXISTS` only checks the *name*. Where a converter could hold an older
shape, check the definition and upgrade/stop rather than silently accept it:

```sql
SET @def := (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='motto');
SET @ddl := CASE
    WHEN @def IS NULL                      THEN 'ALTER TABLE `users` ADD COLUMN `motto` varchar(255) NOT NULL DEFAULT ''''
    WHEN @def = 'varchar(38)'              THEN 'ALTER TABLE `users` MODIFY `motto` varchar(255) NOT NULL DEFAULT '''''
    WHEN @def = 'varchar(255)'             THEN 'DO 0'  -- already correct
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''users.motto has an unexpected type; migrate manually'''
END;
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
```

### 3. Conditional DDL (engine change, guarded ALTER) — PREPARE/EXECUTE

```sql
SET @engine := (SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='marketplace_items');
SET @ddl := IF(@engine = 'MyISAM',
    'ALTER TABLE `marketplace_items` ENGINE=InnoDB ROW_FORMAT=DYNAMIC',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
```

### Data

- **Operator-owned setting:** `INSERT ... ON DUPLICATE KEY UPDATE` of owned
  columns only, or insert-if-absent. Never blanket `INSERT IGNORE` (it hides
  truncation and unrelated key conflicts).
- **Polaris-owned reference row:** explicit upsert of the owned columns.
- **Demo/sample content:** a dev seed, not a migration.
