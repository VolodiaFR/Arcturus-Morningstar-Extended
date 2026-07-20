# Configuration ownership

Polaris loads startup-file or environment values first. Once the database is
ready, `emulator_settings` and `wired_emulator_settings` overlay those values.
Unknown keys remain valid because plugins may own configuration outside the
first-party registry.

Runtime changes made through `ConfigurationManager.update` are marked dirty and
saved during shutdown. Values that were only loaded are not written back.
This means an operator or administration tool may update a different database
setting while Polaris is running without shutdown silently restoring the old
in-memory value. If the same key is changed through the runtime API, that
explicit runtime change is authoritative and is saved.

Subsystem binders support live reload for database-backed hotel settings. Each
key applies independently; malformed values retain the prior value and do not
prevent later settings from applying.
