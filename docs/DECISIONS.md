# Explicit interpretations and implementation decisions

These clarify underspecified details; they do not add gamification, cloud sync,
biometrics, categories, quantity tracking or arbitrary historical editing.

1. **End date is inclusive.** A goal created 2026-10-05 with start 2026-10-05 and
   end 2026-11-04 tracks 2026-10-06 through 2026-11-04: 30 actual tracking days.
   A future configured start tracks from that future date through inclusive end.
2. **Form consistency.** Choosing start or end normalizes end to at least start+1
   and at least the earliest effective tracking day. Invalid data are not persisted.
3. **Editing an unrecorded start** is allowed, but its new effective tracking start
   is at least tomorrow at editing time. This avoids retroactive tracking. Once any
   result exists (including a draft), start is immutable.
4. **Date edits cannot add membership to a finalized day.** Such an edit is rejected,
   rather than rewriting that day's historical denominator or requiring a new answer.
   End dates cannot exclude any recorded result.
5. **Name snapshots have a date-aware source.** NameRevision complements the required
   goalNameSnapshot; pending older dates use the older effective name. Changing an
   entirely unrecorded start resets its name schedule; no result is lost.
6. **Manual archive retains configured end metadata** and stores a separate effective
   trackingEnd=min(configuredEnd, yesterday). A future goal can therefore have an
   empty archived tracking interval without invalidating its original date fields.
7. **Reactivation is a new period of the same logical root.** Insertion order remains
   root creation order. The active root moves back to Goals; old periods remain
   accessible in its detail screen. Archived roots are ordered by latest archive time.
8. **Equal-weight overall statistics** use one weight per logical/root goal. Results
   across that goal's periods within the selected range determine its percentage.
   A period selector exposes independent historical period statistics.
9. **Bugun is not yesterday by default.** With no review finalized today it displays
   Pending, even when older history exists. With several reviews finalized today it
   selects the last by finalizedAt, then by tracking date if timestamps tie.
10. **Explicit goal deletion is the exception to historical preservation.** It
    cascades all that root's periods/results and removes empty day seals. Remaining
    goals' recorded values do not change; aggregates reflect the deletion.
11. **Import format is JSON only**, allowed by the JSON and/or ZIP requirement.
    It excludes PIN credentials and device preferences so resetting a forgotten PIN
    then importing does not reintroduce the unknown PIN. There is no automatic backup.
12. **Import limits:** 20 MiB, 10,000 roots, 50,000 periods, 500,000 results, bounded
    JSON nesting. These exceed the requested hundreds of goals/thousands of results
    while limiting malformed-file abuse. Date fields use validated ISO dates.
13. **Notifications are inexact.** They target the selected business-local time and
    are subject to Android scheduling/permission policies. Timezone remains fixed.
14. **Version selection** uses pinned stable compatible releases, not dynamic/latest
    selectors. Official release pages were inspected, but Maven dependency resolution
    could not be executed in the disconnected build container.
15. **Preferred stack deviations:** explicit constructor DI replaces Hilt;
    strict core JSON codec replaces kotlinx.serialization; KAPT generates Room code.
    Kotlin, Compose, Material 3, Navigation Compose, ViewModel, StateFlow, Room,
    DataStore, AlarmManager, SAF, JUnit and Compose UI tests remain in the project.
