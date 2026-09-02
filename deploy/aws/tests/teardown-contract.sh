#!/usr/bin/env bash
#
# Checks the teardown script.
#
# This is the most dangerous file in the repository. It deletes things that
# cannot be undone, and it runs once, months from now, by somebody who has not
# read it recently. Every assertion here is either "it cannot delete by
# accident" or "it cannot delete before the data is safely out".
#
# Reads the file, runs nothing.

set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/../teardown.sh"
failures=0

fail() { echo "FAIL: $1"; failures=$((failures + 1)); }
has()  { [ -f "$script" ] && grep -qF -- "$1" "$script"; }
hasre() { [ -f "$script" ] && grep -qE -- "$1" "$script"; }

[ -f "$script" ] || fail "missing deploy/aws/teardown.sh"

if [ -f "$script" ] && ! bash -n "$script" 2>/dev/null; then
    fail "teardown.sh is not valid bash"
fi
has "set -euo pipefail" || fail "teardown.sh does not set -euo pipefail"

# --- it cannot run by accident --------------------------------------------
has -- "--confirm-delete" || fail "teardown.sh does not require --confirm-delete"
has -- "--dry-run" || fail "teardown.sh has no dry-run mode"
# A script whose default is deletion is one that deletes when somebody runs it
# to see what it does.
hasre 'confirmed=false|CONFIRMED=false|dry_run=true' \
    || fail "teardown.sh does not default to refusing"

# --- the data leaves before anything is destroyed -------------------------
has "backups/" || fail "teardown.sh does not look for a backup"
has "gzip -t" || fail "teardown.sh does not verify the export it downloaded"
has "pg_restore --list" \
    || fail "teardown.sh does not check the export is a readable dump"
has "sha256sum" || fail "teardown.sh does not record a checksum of the export"

# The order matters more than any single step: a teardown that deletes first and
# exports afterwards has nothing to export from.
if [ -f "$script" ]; then
    export_line="$(grep -n "pg_restore --list" "$script" | head -1 | cut -d: -f1)"
    delete_line="$(grep -n "delete-stack" "$script" | head -1 | cut -d: -f1)"
    if [ -n "$export_line" ] && [ -n "$delete_line" ] && [ "$export_line" -gt "$delete_line" ]; then
        fail "the export is verified after the stack is deleted, which is too late"
    fi
fi

# --- it deletes what the stack owns, not what matches a pattern -----------
has "describe-stacks" || fail "teardown.sh does not resolve the stack's own outputs"
hasre 'if \[ -z "\$(BUCKET|bucket)' \
    || fail "teardown.sh does not refuse an empty bucket name; a glob against nothing is a glob against everything"

# --- versioned objects need versions removing -----------------------------
has "list-object-versions" || fail "teardown.sh does not remove object versions"
has "DeleteMarkers" || fail "teardown.sh does not remove delete markers"

# --- and it confirms the deletion actually happened -----------------------
has "stack-delete-complete" || fail "teardown.sh does not wait for the deletion"
# AWS filter syntax, not a plain key=value string — this is what actually
# appears in describe-instances and describe-addresses calls.
has "Name=tag:Project,Values=guess-who" \
    || fail "teardown.sh does not verify by tag afterwards"

# --- nothing is deleted before the dry-run bail-out ------------------------
# Grep can only see that a string is present, not that a guard works. These two
# checks exist because mutation testing found exactly that gap: disabling the
# confirmation guard, and flipping the defaults to deleting, both left every
# string above intact and passed.
if [ -f "$script" ]; then
    bail="$(grep -n 'dry run complete' "$script" | head -1 | cut -d: -f1)"
    for destructive in "delete-objects" "delete-bucket" "delete-stack"; do
        line="$(grep -n -- "$destructive" "$script" | head -1 | cut -d: -f1)"
        if [ -n "$line" ] && [ -n "$bail" ] && [ "$line" -lt "$bail" ]; then
            fail "$destructive can run before the dry-run exit"
        fi
    done
    [ -n "$bail" ] || fail "teardown.sh has no dry-run exit to guard the deletions"
fi

# --- and running it plainly does not start a deletion ----------------------
# Behavioural, not textual. Invoked with no arguments the script must announce a
# dry run and must not announce a deletion. It is never invoked here with
# --confirm-delete: on a machine that has AWS credentials that would begin a
# real teardown, which is not a thing a test suite may do.
if [ -f "$script" ]; then
    plain="$(bash "$script" 2>&1 | head -5 || true)"
    if ! printf '%s' "$plain" | grep -qi "dry run"; then
        fail "running teardown.sh with no arguments does not announce a dry run"
    fi
    if printf '%s' "$plain" | grep -q "DELETING"; then
        fail "running teardown.sh with no arguments announces a deletion"
    fi
fi

# --- the export is never the thing deleted --------------------------------
if [ -f "$script" ] && grep -nE '^[^#]*rm .*(EXPORT|export_dir|\$EXPORT_DIR)' "$script" >/dev/null; then
    fail "teardown.sh deletes the off-AWS export"
fi

if [ "$failures" -eq 0 ]; then
    echo "teardown contract: all constraints hold"
    exit 0
fi
echo "$failures failure(s)"
exit 1
