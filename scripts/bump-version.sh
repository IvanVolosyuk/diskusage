#!/usr/bin/env bash
# Bumps versionName/versionCode in version.properties.
#
# Usage: scripts/bump-version.sh [patch|minor|major|<explicit version>]
# versionCode = major * 10000 + minor * 100 + patch (5.1.2 -> 50102)
set -euo pipefail

cd "$(dirname "$0")/.."

bump="${1:-patch}"
current=$(sed -n 's/^versionName=//p' version.properties | tr -d '[:space:]')
if [ -z "$current" ]; then
  echo "versionName missing in version.properties" >&2
  exit 1
fi

case "$bump" in
  major|minor|patch)
    IFS=. read -r major minor patch <<< "$current"
    case "$bump" in
      major) major=$((major + 1)); minor=0; patch=0 ;;
      minor) minor=$((minor + 1)); patch=0 ;;
      patch) patch=$((patch + 1)) ;;
    esac
    next="$major.$minor.$patch"
    ;;
  *)
    if ! [[ "$bump" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      echo "Invalid version: $bump" >&2
      exit 1
    fi
    next="$bump"
    IFS=. read -r major minor patch <<< "$next"
    ;;
esac

code=$((major * 10000 + minor * 100 + patch))
printf 'versionName=%s\nversionCode=%s\n' "$next" "$code" > version.properties
printf 'versionName=%s versionCode=%s (was %s)\n' "$next" "$code" "$current"
