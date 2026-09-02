#!/usr/bin/env bash
#
# Checks that installers point at the right server.
#
# This is the one place where a mistake ships to other people. An installer
# built without the endpoint silently talks to localhost — it launches, it looks
# right, and every online game fails on a machine that has no server. An
# installer built with a plain-http endpoint sends passwords in clear over the
# internet. Neither shows up in a diff of the game's code, because neither is in
# the game's code.
#
# Reads files, builds nothing.

set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/../build-installer.sh"
workflow="$here/../../.github/workflows/installers.yml"
failures=0

fail() { echo "FAIL: $1"; failures=$((failures + 1)); }
has()  { [ -f "$1" ] && grep -qF -- "$2" "$1"; }

[ -f "$script" ] || fail "missing packaging/build-installer.sh"
[ -f "$workflow" ] || fail "missing .github/workflows/installers.yml"

if [ -f "$script" ] && ! bash -n "$script" 2>/dev/null; then
    fail "build-installer.sh is not valid bash"
fi

# --- the endpoint reaches jpackage ----------------------------------------
has "$script" "GUESSWHO_SERVER_URL" \
    || fail "the build script does not read GUESSWHO_SERVER_URL"
has "$script" "guesswho.server.url" \
    || fail "the build script does not set the guesswho.server.url property"
has "$script" "--java-options" \
    || fail "the endpoint is not passed to jpackage as a java option"

# --- an unset variable keeps the developer behaviour ----------------------
# Building locally must stay a build against localhost. A script that required
# the variable would make every developer build ceremony.
has "$script" 'GUESSWHO_SERVER_URL:-' \
    || fail "the build script does not treat GUESSWHO_SERVER_URL as optional"

# --- a supplied endpoint must be safe -------------------------------------
has "$script" "https://" \
    || fail "the build script does not require https for a supplied endpoint"

# --- the workflow supplies it and notices when it cannot ------------------
has "$workflow" 'vars.GUESSWHO_SERVER_URL' \
    || fail "the installers workflow does not pass the repository variable"
has "$workflow" "GUESSWHO_SERVER_URL is not set" \
    || fail "the workflow does not fail early when the variable is missing, so a release would quietly ship localhost installers"

if [ "$failures" -eq 0 ]; then
    echo "packaging contract: build script and installers workflow checked, all constraints hold"
    exit 0
fi
echo "$failures failure(s)"
exit 1
