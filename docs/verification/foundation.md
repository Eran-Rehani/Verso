# Reader foundation verification

Checkpoint: complete-reader-upgrade tasks 1–3, 2026-11-11. This is a partial
implementation checkpoint, not the complete reader release.

## Automated checks

The current source passed:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The local invocation used `scripts/check-android` with JDK21, SDK36.1,
executable project-local Gradle cache and Java temporary directories because
this host mounts `/tmp` as `noexec`. Final result: `BUILD SUCCESSFUL in 1m 54s`.

JUnit XML reports: 45 tests, 0 failures, 0 errors.

New coverage includes 25 documents across a database close/reopen, actual Room
schema1→2 validation, conflicting legacy JSON/Room metadata, corruption/missing
files, orphan recovery, interrupted inventory/import/repair, source reattachment,
deleted-book tombstones, cancellation/replacement and stale completion/phase/error
rejection, archive limits for ignored entries/directories and unsafe ZIP paths.

Task-specific read-only reviews approved build, persistence, and intake changes.
Schemas 1 and 2 are exported under `app/schemas/`.

## Data and signing

Room owns metadata and positions. Legacy `recents.json` and conflicting Room
metadata are backed up in app-private `library/migration-v2-backup/`; legacy
payloads remain intact. New imports stage complete generations before committing
metadata. Originals are retained for new imports; old derived documents cannot
reconstruct missing originals. Matching-source re-import can attach an original.
Removing a book currently retains its files behind a metadata tombstone; storage
reclamation and user-controlled backup/restore are follow-up work.

The existing application ID, API26 minimum and signing configuration are
preserved. A development debug key was generated because the original key was
not present. No keys or new APK copies are committed. Compatibility with an
installed APK certificate has not been verified.

## Unavailable checks and remaining scope

Host ADB worked but reported no devices; usable KVM was unavailable. Connected
Android tests, process recreation on a device, TalkBack, RTL interaction, font
scaling and same-device cold-start/open/import/scroll/peak-memory comparisons
remain unverified. Automated JVM tests are not substitutes for those checks.

RaTeX0.1.15 was inspected only as a future integration gate: packaged fonts and
three ABIs are present, and upstream static ELF verification passed 16KB LOAD
and GNU_RELRO alignment for ARM64 and x86_64. It is not yet an app dependency;
no native formula rendering or device loading is claimed at this checkpoint.

Durable logical locations, native equations, standalone TeX, original-page PDFs,
reader search/contents/bookmarks, selection, tables and embedded EPUB images
remain subsequent tasks in the [implementation plan](../plans/complete-reader-upgrade.md).
