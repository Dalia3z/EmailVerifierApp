# Email Verifier App

An Android app (Kotlin + Jetpack Compose + Material 3) that validates a list of
emails (CSV/TXT) using the **emailverifier-kt** library
(`io.github.mbalatsko:emailverifier-kt:1.0.0`) and classifies each address as:

| Status   | Meaning                                                        |
|----------|----------------------------------------------------------------|
| `VALID`  | Deliverable (SMTP server accepted the mailbox).                |
| `INVALID`| Undeliverable / burned / disposable / rejected by the server.  |
| `FAILED` | The verification itself failed (timeout, IOException...).      |
| `PENDING`| Not yet checked (used to resume after a stop/crash).           |

## How it works (the "smart algorithm")

1. **Import** - the user picks a CSV/TXT file through the system file picker (SAF,
   no storage permission needed). Addresses are parsed with **OpenCSV** and stored
   in a **Room** database as `PENDING`.
2. **Verify** - `VerificationService` processes the queue in **chunks of 5**
   concurrently (coroutines + `Dispatchers.IO`) while a **global rate limiter**
   spaces the start of each verification by **500 ms**, protecting the SMTP
   servers (and our IP) from being blocked.
3. **Crash-safe** - every single result is written to Room immediately. If the app
   is killed or the phone loses power, the next launch offers **Resume** from the
   exact point where it stopped.
4. **Live progress** - the UI observes Room counters through `StateFlow` and shows
   a real-time progress bar: `checked / total` + valid/invalid/failed/pending.
5. **Export** - when done, two OpenCSV files are written to
   `Download/EmailVerifier/`:
   - `valid_emails_<timestamp>.csv`
   - `invalid_emails_<timestamp>.csv` (with the failure reason next to each email)

## Error handling

- Timeout / `IOException` / refused connection on any email → recorded as
  `FAILED` with the reason, the batch **continues** without stopping.
- Each library check catches its own exceptions (returned as `CheckResult.Errored`)
  and the mapper converts them into `FAILED`.

## Permissions

- `INTERNET` + `ACCESS_NETWORK_STATE` – SMTP, DNS-over-HTTPS, dataset downloads.
- `WRITE_EXTERNAL_STORAGE` (maxSdk 28) + `READ_EXTERNAL_STORAGE` (maxSdk 32) are
  declared for legacy devices only. Reading uses SAF, writing uses
  MediaStore.Downloads (no permission on Android 10+). On API <= 28 the app
  requests WRITE at runtime before exporting.

## Build & run

1. Open this folder in **Android Studio** (Jellyfish / Koala / Ladybug or newer).
2. Let Gradle sync (Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.20, JDK 17).
3. Run on a device/emulator (minSdk 24, targetSdk 35).

> **Note:** `local.properties` (SDK path) is generated automatically by
> Android Studio - do not commit it.

## Known caveats

- **Port 25** (SMTP) is often blocked on mobile carrier networks; those checks
  then fail with a connection error and are recorded as `FAILED`. Wi-Fi usually
  works better.
- The first verification triggers downloads of the Public Suffix List and the
  disposable-domain list (requires internet). The verifier instance is then cached
  for the whole app lifetime.
- ~10k emails ≈ 5000 s (~83 min) at one check per 500 ms with 5 parallel workers;
  the batch can be paused and resumed at any time.
