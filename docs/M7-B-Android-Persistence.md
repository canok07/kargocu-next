# M7-B: Android save adapter

`AndroidSaveRepository` implements the pure save interface using private application files and platform `AtomicFile` (available below minSdk24). One application-owned instance serializes access with a mutex on `Dispatchers.IO`; AtomicFile itself does not provide threading protection.

Writes validate bytes/revision, reject stale disk revisions, rotate only a valid primary into a separate last-known-good file, sync, atomically commit and verify committed bytes. Corrupt and future primary files require explicit archival before replacement; archives stream and verify the entire original, even when oversized. A preservation authorizes only the exact archived file fingerprint. Candidate reads allocate at most 2 MiB plus one byte so oversized data remains a typed codec error.

Explicit new-game replacement archives both previous candidates first. Each file commits atomically; this is not a cross-file transaction. An interruption before the new primary commits leaves the previous primary authoritative, and both originals remain in archives.

Android instrumentation covers repository recreation, interrupted and abandoned writes, concurrent/stale revisions, future-version protection, explicit recovery, changed-original protection, bounded reads/full archival, and explicit new-game preservation. Nine file-adapter tests and two session persistence tests passed on the Pixel_10a API37 / Android17 emulator on 1 October 2026; both application and test APKs compiled successfully. Physical-device testing has not been performed.

Reference: https://developer.android.com/reference/android/util/AtomicFile
