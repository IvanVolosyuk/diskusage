# Issue triage: IvanVolosyuk/diskusage

This file tracks the open issues of the upstream repository
([IvanVolosyuk/diskusage/issues](https://github.com/IvanVolosyuk/diskusage/issues),
snapshot: November 2026) and what this fork does about them.

Legend:

| Symbol | Meaning |
|---|---|
| ✅ | Addressed by a code change in this repository |
| 🧩 | Partially addressed / mitigation, the remaining part needs something else |
| 📄 | Answered by documentation, no code change possible or sensible |
| ⛔ | Out of scope (needs root, a Google Play account, a specific device or a new feature) |
| 🕰️ | Obsolete: concerns Android versions below the current `minSdk` (24) |

## Build / runtime / platform

| # | Issue | Status | Notes |
|---|---|---|---|
| 104 | Android 15 | ✅ | `compileSdk`/`targetSdk` 37, edge-to-edge insets, 16 KB page-size native libs. |
| 100 | Doesn't work on Android 14 | ✅ | Same as above plus the all-files-access flow on API 30+; volume discovery rewritten. |
| 84 | not compatible with Android 14 | ✅ | Closed upstream, same fix. |
| 106 | Modernize build tooling | ✅ | AGP 9.5, Gradle 9.9, built-in Kotlin, SDK 37, NDK r28c, version catalog, R8. |
| 73 | Support various Android versions | ✅ | `minSdk` 24, `targetSdk` 37, per-version permission handling. |
| 65 | Android 10 compatibility | ✅ | Scoped-storage aware access, `requestLegacyExternalStorage` retained for API 29. |
| 59 | No support android x86(x64) 7.0 | ✅ | All four ABIs are built (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`). |
| 11 | зависает / hangs | 🧩 | The scan worker catches every throwable, the progress dialog is always dismissed, and a stuck helper process is killed after 5 s. A wedged *filesystem* can still look like a hang. |
| 16 | `RuntimeException: Error: no more data` | ✅ | Root cause fixed: the bundled scanner produced no output at all (the exec'd shared object crashed) *and* it emitted a legacy leading NUL byte. Both are fixed; failures still fall back to the Java scanner instead of crashing. |
| 33 | `RuntimeException: Error: no more data` | ✅ | Same fix as #16. |
| 55 | `IOException: Cannot run program "/data/user/0/…"` | ✅ | The scanner is no longer executed as a child process (that is blocked since Android 10 and the ELF segfaulted anyway). It runs in-process through JNI; a failure falls back to the Java scanner instead of crashing. |
| 50 | Version 4.0.2 only for Oreo? | 🕰️ | No longer relevant. |
| 52 | App information missing on 5.1 | 🕰️ | No longer relevant. |

## Storage detection and scanning

| # | Issue | Status | Notes |
|---|---|---|---|
| 83 | Disk Usage does not display internal memory for scan | ✅ | `StorageManager.getStorageVolumes()` is now the primary source, with `getExternalFilesDirs()` as fallback. |
| 78 | Support USB storage | ✅ | USB volumes are reported by `StorageManager` and listed with their description. |
| 85 | Android 13: "Download" not separate, added to system storage | 🧩 | `Download` is part of the primary shared volume by design; it is now reachable in the graph. A separate pseudo volume is not planned. |
| 99 | Hidden directories shown as system | 🧩 | Hidden directories are scanned (they are real space usage). "System data" is computed as *total − visible*, so unreadable areas still land there. Granting all-files access reduces the gap. |
| 90 | Can not delete & scan is slow | 🧩 | The delete confirmation lists content on a worker thread (no more ANR), deletion uses all-files access, and scanning now really uses the native scanner (in-process via JNI) instead of always falling back to the Java implementation. |
| 31 | Removable SD not selectable | ✅ | Volume list rewritten. |
| 28 | Unable to scan external sdcard | ✅ | Volume list rewritten. |
| 27 | External SD card not scanned | ✅ | Volume list rewritten. |
| 21 | Folder `/storage/extSdCard` | ✅ | Volume list rewritten. |
| 13 | `/Removable` doesn't show up | ✅ | Volume list rewritten. |
| 26 | Won't scan "o2 space" folder | ✅ | All mounted volumes are listed. |
| 29 | Internal storage mixed with internal sdcard (CM 11) | 🕰️ | CyanogenMod 11 is far below `minSdk`. |
| 25 | Deleted directories still shown after reboot | 🧩 | Cached trees are dropped on "Rescan"; the stale cache is no longer reused across deletions. |
| 20 | Autoselect when only one volume exists | ✅ | The chooser is skipped when exactly one volume is reported. |
| 17 | Links counted as taking the target space | ✅ | The native scanner uses `lstat()` and never follows symlinks (verified, no change needed). |
| 49 | Memory info doesn't match Android settings | 📄 | `StatFs` reports blocks of the mounted volume (including reserved blocks); Android's settings screen uses a different accounting. |
| 56 | "Where has the space gone?" feature | ⛔ | Feature request, not a bug. |
| 66 | Visualise by number of files | ⛔ | Feature request. |
| 91 | Alternative visualisation (pie chart) | ⛔ | Feature request. |
| 94 | UI changes | 🧩 | Dark mode, real toolbars, adaptive/themed icons and a percentage readout landed; a full redesign is out of scope. |
| 15 | Wider columns | ⛔ | Layout preference. |

## Apps / app storage

| # | Issue | Status | Notes |
|---|---|---|---|
| 98 | cannot read apps | ✅ | The app list no longer depends on `UsageStatsManager` (which only returns recently used apps). |
| 34 | Cannot scan App Storage | ✅ | Same fix; degrades to APK size when usage access is missing. |
| 105 | App cache sizes not showing (OxygenOS 15) | ✅ | `StorageStats.getCacheBytes()` is used again and cache is no longer subtracted from the data size. |
| 67 | Apps reported as System Data | 🧩 | Apps are now listed independently of usage stats, so fewer bytes fall into "System data". |
| 87 | Analysing `/data` with root does not work | ⛔ | Root mode still relies on executing a helper through `su`; modern Android blocks executing app-provided binaries and SELinux restricts direct `/data` reads. Not fixable reliably across ROMs. |
| 58 | Can't delete a file in `/data` when in root mode | ⛔ | Same as above. |
| 39 | How to delete System data | 📄 | System data is not deletable without root. |
| 22 | [ROOT] FolderMount | ⛔ | Root-only integration. |
| 32 | No root access with Lollipop | 🕰️ | Below `minSdk`. |
| 41 | System memory on Galaxy S5 | 🕰️ | Device-specific, below `minSdk`. |

## Opening files / integrations

| # | Issue | Status | Notes |
|---|---|---|---|
| 86 | Regression: no viewer found | ✅ | Content URIs with explicit read grants, real MIME types, `*/*` fallback and directory MIME types (`resource/folder`). |
| 88 | Opening a video in a "strange" mode | ✅ | Same fix: the correct MIME type is sent instead of `binary/octet-stream`. |
| 89 | Double click on a file has no effect | ✅ | Double-tap opens the entry; single tap still selects. |
| 72 | Integration with Material Files | ✅ | `content://` URI + `resource/folder`/`inode/directory`, matching Material Files' intent filters. Folders that no app can open fall back to the system documents provider (Files app) and finally to a copy-path dialog. |
| 74 | Audiobook file type support | ✅ | File type detection now works for any extension known to `MimeTypeMap`, with a `*/*` fallback. |
| 53 | No viewer found | ✅ | Same as #86. |
| 60 | Changing default file manager | 📄 | Android decides which app handles `VIEW`; DiskUsage offers all candidates. |
| 35 | Location of non-installed APK files | 📄 | Use the search function on a directory that contains APK files. |
| 101 | Without actual paths the info is not valuable | 🧩 | The title bar always shows the path of the selected entry; long paths are shortened in the graph. |

## Distribution / project

| # | Issue | Status | Notes |
|---|---|---|---|
| 82 | Release the APK on GitHub | ✅ | Automatic releases with APK + `SHA256SUMS.txt`; nightly prerelease. |
| 95 | Add icon and screenshots to F-Droid | ✅ | `fastlane/metadata/android/{en-US,de-DE}` with icon, feature graphic, screenshots and changelogs. |
| 36 | Create git tags for releases | ✅ | Tags `v<versionName>` are created by the release workflow; `docs/RELEASING.md` documents the scheme. |
| 23 | Sources for 3.4.1 | ✅ | Every release is reproducible from a tag. |
| 92 | Themed icon | ✅ | Adaptive icon with `<monochrome>` layer (Android 13+). |
| 102 | Add Play Store link to the readme | 📄 | The app is not distributed on Google Play; F-Droid and GitHub Releases are the channels. |
| 97 | Google Play not available | 📄 | Same. |
| 63 | App no longer available on Google Play | 📄 | Same. |
| 62 | Android TV | 🧩 | Leanback launcher entry and TV banner added; D-pad navigation already worked. No TV-specific layout. |
| 69 | Kotlin version of the app | 🧩 | Data sources and UI glue are Kotlin; the renderer/scanner core remains Java for performance and history reasons. |
| 57 | Updated zh-rCN translation | 📄 | Translation PRs are welcome; no translation work in this change set. |
| 51 | Estonian translation | 📄 | Same. |
| 18 | Patch for `values/strings.xml` | 🕰️ | Already merged upstream. |
| 14 | Update "Wifi Keyboard" | ⛔ | Unrelated app. |
| 96 | "thanks for the update" | 📄 | Not a defect. |
| 74 | Audiobooks support (see above) | ✅ | — |

## Verification

* `./gradlew assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintDebug`
  pass (AGP 9.5.1, Gradle 9.9.1, JDK 17, SDK 37, NDK r28c).
* The shipped `libscan.so` uses 16 KB LOAD-segment alignment for the 64-bit ABIs
  and the APK is 16 KB zip-aligned; the CI workflow enforces this on every push.
* Runtime smoke test on an Android 15 (API 35) emulator with the minified release
  build: volume detection, native scan, graph, selection, double tap, file/app
  opening, search, renderer switch, delete confirmation and the About dialog were
  exercised. Screenshots are not checked in.
* Device-specific issues (vendor ROMs, root, TV hardware) are marked 🧩/⛔ rather
  than ✅ because they cannot be verified in this environment.
