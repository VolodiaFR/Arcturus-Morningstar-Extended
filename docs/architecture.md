# Polaris architecture

Polaris is a Java 25 game server for Nitro-compatible Habbo hotels. The Maven
module lives in `Emulator/`; the Nitro client, CMS, proxies, and plugins are
separate integrations.

## Compatibility facades

`Emulator`, `GameEnvironment`, `Room`, `RoomManager`, and the established
manager classes remain the public compatibility facades used by legacy code
and precompiled plugins. Internal implementations may be extracted behind
them, but public signatures, fields, live collections, packet layouts, and
classloading behavior remain stable.

```text
Plugins, clients, CMSs, and proxies
                 |
                 v
Emulator / GameEnvironment / Room facades
                 |
                 v
Runtime services / room components / repositories
                 |
                 v
MariaDB / task executors / Netty transports
```

## Runtime ownership

`PolarisBootstrap` constructs the process in ordered phases.
`PolarisRuntime` owns core process services, while `HotelServiceRegistry`
records hotel-domain resources and disposes verified lifecycle owners in
reverse order. Legacy static getters continue to forward to these owners.

## Domain and persistence boundaries

Room construction, loading, persistence, lifecycle, item indexing, placement,
movement, and ownership are internal components behind `Room`. High-change
managers follow the same pattern: protocol handlers parse and compose packets,
application services own orchestration, and repositories receive explicit
database dependencies.

Flyway migrations under `Emulator/src/main/resources/db/migration/` are the
runtime schema source of truth. The legacy SQL bridge keeps supported old
plugin queries compatible; new Polaris code targets the current schema.

## Networking and concurrency

Netty event loops own socket work. Blocking HTTP, authentication, crypto, and
persistence tasks use bounded workload-specific executors. Per-channel packet
ordering is preserved, sustained unwritable channels are bounded, and internal
broadcast optimizations retain the public defensive-copy contract.

## Configuration and quality gates

Typed configuration metadata documents first-party keys while unknown
plugin-owned keys remain allowed. CI runs unit and Testcontainers integration
tests, ABI and assembled-jar compatibility checks, formatting and architecture
ratchets, static analysis, coverage, and supported MariaDB migration matrices.
