# Wired random-selection JFR profile

This profile exercises the fallback random-effect selector used when a wired
stack enables random execution without a `WiredExtraRandom` item. It performs
2,000,000 selections after a 100,000-selection warmup and records allocation
events from the real `WiredEngine` selection seam.

Run it from the repository root with JDK 25:

```bash
./Emulator/mvnw -f Emulator/pom.xml test \
  -Dtest=WiredRandomSelectionJfrProfileTest \
  -Dpolaris.profile.wired-random=true \
  -Dpolaris.profile.wired-random.variant=local
```

The recording and text summary are written under
`Emulator/target/profiles/wired-random-local.*`.

## Result

Captured on 2026-07-20 with Temurin 25.0.3+9:

| Measurement | `new Random()` | `ThreadLocalRandom` |
| --- | ---: | ---: |
| Selections | 2,000,000 | 2,000,000 |
| Recording window | 83.408 ms | 56.991 ms |
| Recorded `Random` allocations | 7 | 0 |
| Recorded `Random` allocation bytes | 224 | 0 |
| Total recorded allocations | 75 | 1 |
| Total recorded allocation bytes | 18,232 | 24 |

JFR allocation events are sampled at allocation boundaries rather than a
complete object count, so the recorded allocation totals are comparative
signals. The optimized run removed every sampled `java.util.Random`
allocation while preserving index bounds and invalid-bound behavior.
