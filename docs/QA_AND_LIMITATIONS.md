# QA status — 2026-10-07

## Executed

- Domain compilation: PASS with the available Kotlin 1.9 compiler/JDK 21.
  Source uses language constructs compatible with this compiler; Android Gradle
  build is configured for Kotlin 2.2.21/JDK 17.
- Domain specifications: **82/82 PASS**, identical cases shared with Gradle/JUnit.
- Static XML/resource/source-policy checks: PASS.
- Kotlin syntax parsing: PASS. This is NOT Android API resolution or type-checking.
- Actual Android build attempt: BLOCKED before compilation (no SDK/Gradle).
- Actual Gradle download bootstrap: BLOCKED by DNS/network failure.

## Not executed / not proven

- Dependency resolution and Android compilation, KAPT generation, DEX packaging.
- Android lint; R8 release optimization; generated Room schema validation.
- Debug or release APK creation, signing verification, APK metadata inspection.
- Real Android install, launch, OS process death/relaunch, permissions, device storage.
- 10 Room integration tests and 5 Compose smoke tests (included, NOT RUN).
- Exhaustive UI coverage of all required workflows. Current automated UI tests are
  a smoke suite, not the whole acceptance plan below.
- Full TalkBack, large-font, contrast and OEM-specific notification behavior testing.
- Full Material 3 visual QA on real screens. No simulated image is presented as
  a screenshot of the app running on Android.

**No production-ready or installable-APK claim is made for this source package.**
The connected GitHub account was not modified; no external repository, CI run,
paid service, release or signing key was created on the user's behalf.

## Required device acceptance plan (pending)

| Group | Checks to execute |
|---|---|
| First launch | Offline startup; empty Home; exactly five bottom destinations; persistent add FAB |
| Creation | Empty name disables save; 50-character limit; Unicode; 7/30/90 duration; invalid-date correction; creation day excluded |
| Review | Oldest date only; insertion order; toggle before finalization; last-answer auto-finalization; next pending opens; process restart preserves drafts |
| History | Finalized result cannot be changed; rename preserves snapshots; per-period calendar; expected Pending versus not-applicable states |
| Edit | Any result locks start; end can extend/shorten without affecting recorded history; blocked retroactive finalized membership |
| Archive | Automatic archive only after last relevant day; manual archive excludes today; old pending retained; reactivation starts tomorrow |
| Deletion | Confirmation; cascade; aggregate recomputation; remaining drafts finalize when appropriate |
| Statistics | Arithmetic mean of nonempty goal percentages; daily all-done counts; Monday week; month; custom single date/range; zero and pending exclusions |
| Charts | Line/bar selection, dates/counts/percentage dialog; accessible text equivalent; scrolling with long history |
| PIN | Setup, cold launch, background relock, unlimited incorrect tries, correct retry, change, disable, malformed credentials, forgotten-PIN message |
| SAF | Export plain JSON; import empty only; corrupt schema/UTF-8/IDs rejected; cancel picker; relock while external picker is open; export before delete-all |
| Settings | All three languages, light/dark/system, dynamic text scaling, persisted values after force-stop/relaunch |
| Reminder | Runtime denial/grant; default 08:00; changed time; no pending=no notification; tap opens Home; reboot and OS time changes |
| Backup | Android cloud/transfer policies on target OEMs; no unexpected server traffic or app-generated automatic backup |
| Performance | Hundreds of roots, thousands of rows, multi-year history; scroll responsiveness and memory allocation profiling |

## Evidence files

`reports/core-tests.txt` — actual case-by-case console output.
`reports/core-tests.xml` — actual offline JUnit-style result XML.
`reports/source-check.json` — static checks and their scope.
`reports/kotlin-syntax-check.txt` — parser output, explicitly not a build.
`reports/android-build-attempt.txt` — local build precondition failure.
`reports/gradle-bootstrap-attempt.txt` — actual network failure.
`docs/SOURCES.md` — primary documentation consulted for versions/platform behavior.

The CI verification script rejects a missing, empty, corrupt, unsigned, wrong-package,
wrong-version, wrong-SDK, or nonlaunchable APK. It does not fabricate a PASS report
when no APK exists. Optional `--install` additionally needs an emulator or device.
