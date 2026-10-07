# Architecture

## Modules

`core`: immutable Kotlin models, centralized BusinessClock, GoalRules,
ReviewEngine, Statistics, BackupValidator/BackupCodec, PinHasher,
GoalRepository interface and SaveResultUseCase. No Android dependency.

`app/data`: Room entities/DAO, LocalGoalRepository, SettingsStore.
`app/presentation`: MainViewModel, editor ViewModel, Compose screens and navigation.
`app/platform`: Android notification/reminder scheduling and localized context.

Data direction:
User intent → ViewModel/use case → repository → Room transaction → invalidation
Flow → ViewModel immutable UiState → Compose. Settings use DataStore Flow.
Constructor dependency injection is explicit in AppGraph; Hilt is not used.

## Schema

Goal 1:N GoalPeriod 1:N DailyResult. Goal insertionOrder is unique.
Period (goalId, periodOrder) is unique. Result (periodId, date) is unique.
Foreign keys cascade only when the user explicitly deletes a goal or all data.

NameRevision stores the name effective from a business date, so unreviewed
older days do not accidentally get a later name. DailyResult stores its own
immutable goalNameSnapshot as required.

FinalizedDay stores the tracking date and the actual finalization timestamp.
It distinguishes a tracking day from a review performed today. A tie on the
millisecond finalization timestamp is broken by tracking date (sequential workflow).

Room version is 1; no destructive migration fallback exists. Room schema export
is configured. A generated schema JSON will first exist after real annotation
processing in an Android build; it is not fabricated in this source delivery.

## Transactional review

1. Read relevant period metadata, existing day seals and this day's draft rows.
2. Find the earliest eligible date through yesterday, excluding finalized days.
3. Reject requests for any other date, any finalized date or an inapplicable period.
4. Upsert the selected binary result, preserving any existing name snapshot.
5. When all applicable periods have a result, mark all results finalized and
   insert one FinalizedDay in the SAME transaction.
6. Archive expired periods only after their last applicable date is finalized.
7. UI observes new oldest pending date; there is no finalization button or
   intermediate result screen.

Deleting an unanswered goal or shortening an unrecorded part of a period can leave
all remaining draft answers complete. The same transaction re-evaluates and
finalizes them. Finalized rows cannot be updated even by accidental DAO upsert:
a SQLite BEFORE UPDATE trigger aborts it.

## Query and memory strategy

Main screens read goal/period metadata plus day seals, not all historical
DailyResult rows. Current review reads only its single date. Charts read aggregate
daily counts for the requested range. Goal history fetches only the viewed month.
SQL computes goal/day counts; core computes the equal-weight mean of aggregates.
Only explicit export necessarily loads all records. UI IO runs off the main thread.

The pending algorithm merges overlapping tracking intervals before counting days.
The import validator uses grouped foreign-key maps and a sweep-line count of
applicable periods for finalized days, avoiding a day-by-period roster allocation.

## Security and backup

No INTERNET permission or server. Automatic Android backup and device transfer
are excluded by manifest/XML policies. SAF gives access only to the user-selected
file; no broad filesystem permission is requested.

PIN: PBKDF2WithHmacSHA256, 210,000 iterations, 32-byte random salt, 256-bit hash,
constant-time comparison. A short PIN and unlimited attempts remain inherently
limited protection; no encryption or recovery claim is made. Credentials never
appear in exported JSON. DataStore corruption is not silently treated as no PIN.

The backup codec is an explicit strict, dependency-free JSON reader/writer, not
kotlinx.serialization. This makes actual round-trip tests executable with the
available standalone Kotlin compiler. It rejects duplicate keys, excessive depth,
invalid escapes, noninteger numeric fields, trailing data and files over 20 MiB.
The SAF layer rejects malformed UTF-8. Schema/IDs/dates/foreign keys/ranges/rosters
are validated before insertion, and database emptiness is re-checked transactionally.

## Notifications

A one-shot inexact AlarmManager alarm is rescheduled for the next selected
Asia/Tashkent time. It only notifies when pending dates exist, opens Home, handles
runtime permission on Android 13+, and reschedules after reboot/package replacement.
No exact-alarm special access is requested. Android may defer inexact notifications;
minute-exact delivery is not promised. Receiver implementation still needs real
platform testing on supported Android/OEM versions.
