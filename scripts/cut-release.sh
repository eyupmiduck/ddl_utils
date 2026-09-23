#!/bin/sh
# Cut a release by tagging the current commit and pushing the tag.
#
# Usage: scripts/cut-release.sh [--yes] <version>
#   e.g. scripts/cut-release.sh 0.1.0
#        scripts/cut-release.sh --yes 0.1.0
#
# The tag (v<version>) is the single source of truth for the release version:
# the Release workflow derives -Drevision from it, so the POM is not edited.
# The script refuses to run unless the working tree is clean and HEAD is an
# up-to-date main, which keeps a release on a commit CI has already built
# green. Pass --yes to skip the confirmation prompt for non-interactive use.

set -eu

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

usage() {
    cat <<'EOF'
Usage: scripts/cut-release.sh [--yes] <version>
   e.g. scripts/cut-release.sh 0.1.0
        scripts/cut-release.sh --yes 0.1.0

Tags the current commit v<version> and pushes the tag, which triggers the
Release workflow. The version is the tag name without the leading "v".
EOF
}

yes=
version=
for arg in "$@"; do
    case "$arg" in
        -y | --yes)
            yes=1
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        -*)
            echo "unknown option: $arg" >&2
            usage >&2
            exit 1
            ;;
        *)
            if [ -n "$version" ]; then
                echo "unexpected extra argument: $arg" >&2
                usage >&2
                exit 1
            fi
            version="$arg"
            ;;
    esac
done

if [ -z "$version" ]; then
    echo "a version is required" >&2
    usage >&2
    exit 1
fi

# Accept either 0.1.0 or v0.1.0.
version="${version#v}"

if ! printf '%s' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.]+)?$'; then
    echo "invalid version '$version': expected <major>.<minor>.<patch> (optionally with a suffix)" >&2
    exit 1
fi

tag="v$version"

if ! command -v git >/dev/null 2>&1; then
    echo "git is required but was not found on PATH" >&2
    exit 1
fi

# The tag is the release version, so it must point at a pristine, up-to-date
# main: a dirty tree or a stale main could tag a commit CI has not built.
if [ -n "$(git status --porcelain)" ]; then
    echo "working tree is not clean; commit or stash your changes first" >&2
    git status --short >&2
    exit 1
fi

branch="$(git rev-parse --abbrev-ref HEAD)"
if [ "$branch" != "main" ]; then
    echo "releases must be cut from main (currently on '$branch')" >&2
    exit 1
fi

echo "Fetching origin..." >&2
git fetch --quiet origin main

head="$(git rev-parse HEAD)"
remote="$(git rev-parse origin/main)"
if [ "$head" != "$remote" ]; then
    echo "main is not up to date with origin/main; pull or push first" >&2
    exit 1
fi

if git rev-parse -q --verify "refs/tags/$tag" >/dev/null 2>&1; then
    echo "tag $tag already exists locally" >&2
    exit 1
fi
if git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then
    echo "tag $tag already exists on origin" >&2
    exit 1
fi

if [ -z "$yes" ]; then
    printf 'Tag %s at %s and push it? [y/N] ' "$tag" "$head"
    read -r reply || reply=
    case "$reply" in
        y | Y | yes | YES) ;;
        *)
            echo "aborted" >&2
            exit 1
            ;;
    esac
fi

git tag -a "$tag" -m "Release $tag"
if ! git push origin "$tag"; then
    echo "failed to push $tag; remove the local tag with: git tag -d $tag" >&2
    exit 1
fi

echo "Pushed $tag. The Release workflow will publish to GitHub Packages:" >&2
echo "  https://github.com/eyupmiduck/ddl_utils/actions/workflows/release.yml" >&2
