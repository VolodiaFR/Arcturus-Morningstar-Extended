# Versioning and release policy

Polaris uses `major.minor.patch` versions. Routine compatible releases advance
the patch version. Additive release trains may advance the minor version after
operator-facing review. A breaking public integration change requires an
explicitly approved major-release policy; routine modernization must remain
compatible.

## Release gate

No release is published unless the source commit has a green `verify` run.
The release workflow follows a successful CI run on `main`, rebuilds with
tests, records a SHA-256 checksum, and publishes the runnable jar, SBOM, and
provenance attestations. Manual dispatch is restricted to `main` and uses the
same tested build.

## Release checklist

1. Merge the ordered pull-request stack only after required checks are green.
2. Confirm ABI, precompiled plugin, assembled-jar, and supported MariaDB
   migration jobs pass.
3. Review migrations and configuration changes for upgrade safety.
4. Deploy to a canary hotel and exercise startup, login, rooms, catalog,
   wired, plugins, and shutdown.
5. Monitor startup, database, scheduler, packet, and plugin errors before
   widening rollout.
6. Retain the previous jar, configuration, and database backup for rollback.

Database migration rollback depends on the affected table engines and must not
be described as transaction-wide without executable evidence.
