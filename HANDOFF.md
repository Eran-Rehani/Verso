# Verso reader upgrade: session handoff

## Goal and checkpoint

Finish the [complete reader upgrade](docs/plans/complete-reader-upgrade.md) in
dependency order. The user requested stopping at this checkpoint and continuing
in a later session. **Tasks 1–3 are complete; tasks 4–10 remain.** No task-4
production code has been started.

Foundation commit: `2334e6f5f84dd8ebd644a0a1725b35e626914bf5`, pushed normally to
`Eran-Rehani/Verso` `origin/main` by a free DeepSeek/OpenCode worker. Independent
`git ls-remote` verification matched that commit. This handoff follows in a
documentation commit. No force push, tags, releases, keys or new APK copies.

## Completed and verified

- Restored checksum-verified Gradle 9.3.1 wrapper, pinned JDK 21, installed SDK 36.1,
  documented setup, removed unused Firebase/network/Moshi declarations, added CI.
- Room now owns metadata/positions. Explicit schema 1 to 2 migration and exported
  schemas; legacy JSON/Room backups, stable timestamps/IDs, orphan/corrupt/missing
  records, staged complete generations, restart recovery and retained originals.
  Home limits recents to 20 without deleting books. Removal uses tombstones;
  files remain on disk. See [BookRepository](app/src/main/java/app/verso/reader/data/repository/BookRepository.kt).
- One [ImportCoordinator](app/src/main/java/app/verso/reader/intake/ImportCoordinator.kt) handles picker/share/clipboard intake,
  IO acquisition, cancellation/replacement and guarded UI completion. Sources
  are limited to 64 MiB; ZIP expansion to 256 MiB total, 32 MiB per entry, 10,000 entries,
  counting skipped entries. Paths and cancellation are checked.
- App-focused README authored by a free OpenCode DeepSeek worker, reviewed and
  corrected; CONTRIBUTING and ROADMAP drafted with free OpenRouter models.

Final local check passed **45 tests, zero failures/errors**, lint and debug APK
assembly: `BUILD SUCCESSFUL in 1m 54s`. Three task-specific read-only reviews
approved. [Verification details](docs/verification/foundation.md).

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
scripts/check-android
```

Use the helper locally: this host's `/tmp` is `noexec`; AAPT2 and Room SQLite
failed there. The helper uses executable ignored `.gradle/` cache/temp paths.
Original signing settings are preserved, but the original debug key was absent;
a new ignored development key was generated. Installed-APK compatibility is
unverified. No Android device or usable KVM was available; device tests,
screenshots, accessibility and performance measurements remain unverified.

GitHub [Android run 43096804827](https://github.com/Eran-Rehani/Verso/actions/runs/43096804827)
was **queued**, with no job results, when this handoff was prepared. This is not
a CI pass; inspect the latest runs next session.

## Next action and remaining work

Start task 4: backward-compatible versioned documents, deterministic block IDs,
shared reflow/table/PDF addresses and a normalization mapping that retains
targets/UTF-16 offsets through filtering, splitting and atomic formulas. Existing
resume remains block/pixel based; logical-location columns are reserved, unused.

Then follow the plan: 5 native RaTeX; 6 TeX subset; 7 original/scanned PDF mode;
8 contents/search/bookmarks and lifecycle position flushing; 9 tables, selection,
code copying and EPUB images; 10 screenshots, notices/changelog, integrated
device/performance verification and final publication. Preserve offline/API 26
behavior and existing settings/data. Current PDFs reflow only; math shows source.

RaTeX 0.1.15 AAR passed static ARM64/x86_64 16 KB LOAD/RELRO checks and includes
fonts/three ABIs. It is **not integrated**; native loading/rendering is untested.

User authorized Codex direct implementation, free OpenRouter/OpenCode for simple
work, and direct DeepSeek API via pi-bolt (`deepseek/deepseek-flash`). Paid
OpenRouter failed for insufficient credits; do not repeat that route. Keep
credentials/security work local. Local detailed ledger and worker logs are
referenced in `.superpowers/sdd/complete-reader-upgrade/progress.md` (git-ignored).
