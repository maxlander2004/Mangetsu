#!/usr/bin/env bash
# Fails when a release tag does not match the app version it would publish, so a release can never
# ship APKs built from a commit that claims another version.
#
# Usage: scripts/release-tag-check.sh <tag> [gradle-file]
set -euo pipefail

tag="${1:-}"
gradle_file="${2:-app/build.gradle.kts}"

if [ -z "$tag" ]; then
  echo "usage: $0 <tag> [gradle-file]" >&2
  exit 2
fi

if [ ! -f "$gradle_file" ]; then
  echo "no such file: $gradle_file" >&2
  exit 2
fi

version="$(sed -n 's/^val _versionName = "\(.*\)"$/\1/p' "$gradle_file" | head -1)"

if [ -z "$version" ]; then
  echo "could not read _versionName from $gradle_file" >&2
  exit 2
fi

expected="debug-$version"

if [ "$tag" != "$expected" ]; then
  echo "tag '$tag' does not match the version in ${gradle_file##*/} (expected '$expected')" >&2
  exit 1
fi

echo "tag $tag matches version $version"
