# M7-A: Pure save codec and store

M7-A adds persistence contracts and behavior to the pure Kotlin/JVM `:game` module. It is a bounded part of M7, not completion of the whole M7 milestone.

## Save format

`SaveCodec` writes a strict JSON envelope containing:

- save, state, and catalog versions
- a monotonic persisted revision
- the complete `GameState` payload, including active jobs
- a SHA-256 checksum of the serialized payload

Decode is bounded to 2 MiB by default. It rejects malformed UTF-8/JSON, missing or unknown envelope fields, invalid field types, checksum mismatches, unsupported old versions, metadata mismatches, invalid catalog references, and invalid persisted ranges. Unknown future save versions return their original bytes as a protected `FutureVersion` result.

`SaveMigrations` currently supports the checked-in version 1 golden fixture and migrates it to version 2 by adding revision zero. The source checksum is verified before migration.

## Store contract

`SaveRepository` remains a pure interface. `GameStore` serializes commands through a mutex and performs reduce, validate, save, then publish. A failed, stale, or throwing repository write cannot publish the candidate state; coroutine cancellation propagates.

`GameStore` accepts the `GameCatalog` used for post-reduce validation. Callers using a non-default catalog must inject the same catalog into `GameEngine`, `SaveCodec`, and `GameStore`.

Corrupt or future primary saves enable recovery protection. Recovery is explicit: the repository must preserve the original primary before a recovered candidate can replace it. A successful reopen clears obsolete recovery protection.

## Verification

`./gradlew -PgameOnly=true :game:test` passes 55 tests: the existing 37 pure engine tests and 18 persistence tests.

## Not included

M7-A does not implement an Android repository, `AtomicFile`, UI/ViewModel wiring, device tests, fleet changes, or economy changes. Android persistence and the remaining M7 work require separate follow-up milestones.
