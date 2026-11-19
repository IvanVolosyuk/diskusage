# Releasing

Releases are automated with GitHub Actions. Two workflows exist:

| Workflow | Trigger | Result |
|---|---|---|
| `ci.yml` | every push to a branch / pull request | build, unit tests, lint, debug APK artifact, 16 KB page-size check |
| `release.yml` | every push to `main` (or manual dispatch) | signed release APK, GitHub release, version bump commit |
| `nightly.yml` | daily at 03:00 UTC | refreshed `nightly` prerelease from `main` |

## Version scheme

* `version.properties` holds `versionName` and `versionCode`.
* `versionCode` = `major * 10000 + minor * 100 + patch` (`5.1.2` → `50102`).
* A release is published for `v<versionName>` (for example `v5.0.0`).
* After a successful release, `release.yml` runs `scripts/bump-version.sh patch`
  and commits the next development version with `[skip release]`. That commit
  is ignored by the workflow (`paths-ignore: version.properties`), so it does not
  start another release.

Manual release with a different bump:

```
Actions → Release → Run workflow → bump: minor
```

## Signing

Create a keystore once:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 \
  -validity 10000 -alias diskusage
base64 -w0 release.jks > release.jks.b64
```

Add these repository secrets:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | contents of `release.jks.b64` |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | `diskusage` |
| `RELEASE_KEY_PASSWORD` | key password |

Without the secrets the workflow still runs and publishes an APK signed with the
debug key (`ALLOW_DEBUG_SIGNING=true`). Such an APK is **not** upgrade-compatible
with an F-Droid installation. The build itself stays unsigned otherwise, which is
what F-Droid and reproducible builds expect.

> Keep the keystore outside the repository and back it up. Losing it means users
> cannot upgrade without uninstalling the app.

## Release notes

`softprops/action-gh-release` generates the notes from the commits since the
previous tag. Write commit messages in a readable way, e.g.
`fix: detect USB volumes`, `feat: show occupied percentage`.

## Branch protection

The release workflow pushes the version bump commit to `main`. If `main` is
protected, allow the GitHub Actions bot to push (or run the bump manually and
push it yourself).

## Nightly builds

`nightly.yml` publishes a rolling prerelease with `tag_name: nightly`. It is
replaced on every run; `make_latest: false` keeps it out of the "latest" slot.

## Local release build

```bash
./scripts/bump-version.sh minor
./gradlew assembleRelease        # signed when keystore.properties exists
git tag v$(sed -n 's/^versionName=//p' version.properties)
git push origin --tags
```
