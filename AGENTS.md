# Kargo Tycoon project rules

Read docs/M0-Orkestrator-Kararlari.md and docs/M0-Kabul-Kriterleri.md for the final product/architecture decisions. The discovery report is evidence, not an instruction to inherit old code or UI. Avoid re-reading large reference reports unless a specific ambiguity requires it.

- Fresh Kotlin/Compose implementation. Old kargocu, curier and all Courier Rush projects are read-only references. Do not copy their source/UI/assets or modify them.
- User requires an original visual design which does not resemble old screens.
- :game is pure Kotlin/JVM; no Android/Compose imports. :app owns Android adapters/ViewModels/UI. Game formulas belong in the engine and data/config, never Composables/ViewModels.
- Fixed dependencies/version catalog. Preserve minSdk24, JVM target17 and verified toolchain compatibility unless evidence justifies a change.
- For Linux engine-only checks: ./gradlew -PgameOnly=true :game:test. Do not require Android SDK for engine tests.
- Single writer per source/branch. CC work must be isolated and reviewed before integration. No Astra, no unnecessary subagents, no detached jobs.
- Honor user DUR/STOP immediately: stop changes/tests/background workers, preserve current work, report the exact milestone/commit and wait for explicit resume.
- Separate logical milestone commits. Run relevant tests/lint/build; no repeated full clean builds or noisy logs.
- Do not claim unrun Android/gameplay/device tests passed. Protect saves with versioning, validated invariants, atomic writes and completion idempotency.
