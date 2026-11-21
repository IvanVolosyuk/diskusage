# Contributing

Thanks for helping with DiskUsage.

## Before you start

* The app targets Android 17 (API 37) and supports Android 7.0 (API 24) and
  newer.
* Please keep changes focused. A bug fix should not contain unrelated
  refactoring.
* Java and Kotlin are both used. New UI/data code should be Kotlin; the scanner
  and renderer cores stay Java (they are performance critical and use a lot of
  bit twiddling).

## Build

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Required SDK components:

* `platforms;android-37.0`
* `build-tools;36.0.0`
* `ndk;28.2.13676358`

## Pull requests

1. Fork the repository and create a topic branch.
2. Make sure `./gradlew testDebugUnitTest lintDebug assembleDebug` passes.
3. Update `CHANGELOG.md` under `## [Unreleased]` for user-visible changes.
4. Describe *what* changes and *why*; mention related issue numbers.
5. Do not add gradle wrappers, binaries or generated files.

## Translations

Translations live in `app/src/main/res/values-<locale>/strings.xml`. Adding a
locale additionally requires:

* an entry in `app/src/main/res/xml/locales_config.xml`,
* a `fastlane/metadata/android/<locale>/` folder (title, descriptions,
  changelog) if store metadata should be translated.

## Commit messages

Conventional Commits are appreciated as they feed the release notes:

```
fix: detect USB volumes on Android 14
feat: show the occupied percentage
docs: explain the F-Droid submission
```

## Reporting bugs

Use the GitHub issue template. A useful report contains:

* Android version and device/ROM,
* whether "All files access" and "Usage access" are granted,
* steps to reproduce, expected and actual behaviour,
* a logcat excerpt (`adb logcat | grep -i diskusage`) for crashes.

## License

By contributing you agree that your changes are licensed under the GNU General
Public License version 2 or later, like the rest of the project.
